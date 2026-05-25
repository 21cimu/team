"""
使用训练好的模型进行摄像头/视频动作识别，并在画面上展示动作分数。
"""

import argparse
import json
import os
import time
from collections import deque
try:
    import tkinter as tk
except ImportError:
    tk = None

import cv2
import mediapipe as mp
import numpy as np
import torch
try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    Image = None
    ImageDraw = None
    ImageFont = None

from train_model import ActionRecognitionModel, build_features

ACTION_NAME_ZH = {
    "BenchPress": "卧推",
    "BodyWeightSquats": "徒手深蹲",
    "CleanAndJerk": "抓举挺举",
    "FloorGymnastics": "自由体操",
    "HandstandPushups": "倒立俯卧撑",
    "HandstandWalking": "倒立行走",
    "HighJump": "跳高",
    "JumpRope": "跳绳",
    "JumpingJack": "开合跳",
    "LongJump": "跳远",
    "Lunges": "弓步蹲",
    "ParallelBars": "双杠",
    "PoleVault": "撑杆跳",
    "PommelHorse": "鞍马",
    "PullUps": "引体向上",
    "PushUps": "俯卧撑",
    "RockClimbingIndoor": "室内攀岩",
    "RopeClimbing": "爬绳",
    "StillRings": "吊环",
    "TrampolineJumping": "蹦床",
    "UnevenBars": "高低杠",
    "WallPushups": "墙面俯卧撑",
}


def rgb(r, g, b):
    return (b, g, r)


FONT_CANDIDATES = [
    "C:/Windows/Fonts/SourceHanSansSC-Regular.otf",
    "C:/Windows/Fonts/SourceHanSansCN-Regular.otf",
    "C:/Windows/Fonts/NotoSansCJKsc-Regular.otf",
    "C:/Windows/Fonts/NotoSansSC-Regular.ttf",
    "C:/Windows/Fonts/msyh.ttc",
    "C:/Windows/Fonts/msyhbd.ttc",
    "C:/Windows/Fonts/simhei.ttf",
]
TECH_FONT_CANDIDATES = [
    "C:/Windows/Fonts/DINAlternate-Bold.ttf",
    "C:/Windows/Fonts/DIN Alternate Bold.ttf",
    "C:/Windows/Fonts/DINAlternate.ttf",
    "C:/Windows/Fonts/DIN Alternate.ttf",
    "C:/Windows/Fonts/Orbitron-Regular.ttf",
    "C:/Windows/Fonts/ShareTechMono-Regular.ttf",
    "C:/Windows/Fonts/CascadiaMono.ttf",
    "C:/Windows/Fonts/consola.ttf",
    "C:/Windows/Fonts/consolab.ttf",
    "C:/Windows/Fonts/Bahnschrift.ttf",
]
LOW_SCORE_HINT_THRESHOLD = 0.60
LEFT_SHOULDER = 11
RIGHT_SHOULDER = 12
LEFT_ELBOW = 13
RIGHT_ELBOW = 14
LEFT_WRIST = 15
RIGHT_WRIST = 16
LEFT_HIP = 23
RIGHT_HIP = 24
LEFT_ANKLE = 27
RIGHT_ANKLE = 28


def parse_args():
    parser = argparse.ArgumentParser(description="实时动作识别与分数可视化")
    parser.add_argument(
        "--source",
        default="0",
        help="视频源，默认 0 表示摄像头，也可以传入本地视频路径",
    )
    parser.add_argument(
        "--model-path",
        default="./trained_models/best_model.pth",
        help="训练好的模型路径",
    )
    parser.add_argument(
        "--class-mapping",
        default="./processed_data/class_mapping.json",
        help="类别映射文件路径",
    )
    parser.add_argument(
        "--sequence-length",
        type=int,
        default=100,
        help="用于推理的时序长度，默认 100 帧",
    )
    parser.add_argument(
        "--top-k",
        type=int,
        default=3,
        help="画面上显示前 k 个动作分数",
    )
    parser.add_argument(
        "--min-frames",
        type=int,
        default=20,
        help="至少累计多少帧后开始给出较稳定的预测",
    )
    parser.add_argument(
        "--score-threshold",
        type=float,
        default=0.10,
        help="只展示分数不低于该阈值的动作",
    )
    parser.add_argument(
        "--smooth-factor",
        type=float,
        default=0.7,
        help="分数平滑系数，越大越平稳",
    )
    parser.add_argument(
        "--ui-scale",
        type=float,
        default=1.3,
        help="界面字号和面板缩放比例，默认 1.3，可调大或调小",
    )
    return parser.parse_args()


def resolve_video_source(source):
    if source.isdigit():
        return int(source)
    return source


def load_class_names(class_mapping_path):
    with open(class_mapping_path, "r", encoding="utf-8") as f:
        mapping = json.load(f)
    return mapping["categories"]


def build_display_names(class_names):
    return [ACTION_NAME_ZH.get(name, name) for name in class_names]


def load_model(model_path, device):
    if not os.path.exists(model_path):
        raise FileNotFoundError(f"模型文件不存在: {model_path}")

    checkpoint = torch.load(model_path, map_location=device)
    config = checkpoint["config"]
    state_dict = checkpoint["model_state_dict"]

    classifier_weight_keys = [
        key for key in state_dict.keys() if key.startswith("classifier.") and key.endswith(".weight")
    ]
    if classifier_weight_keys:
        last_classifier_weight = max(classifier_weight_keys, key=lambda key: int(key.split(".")[1]))
        num_classes = state_dict[last_classifier_weight].shape[0]
    else:
        num_classes = config.get("num_classes", 22)

    model = ActionRecognitionModel(
        input_size=config.get("input_size", 132),
        hidden_size=config["hidden_size"],
        num_layers=config["num_layers"],
        num_classes=num_classes,
        dropout=config["dropout"],
    ).to(device)
    model.load_state_dict(state_dict)
    model.eval()
    return model, config


def extract_pose_sequence(frame_bgr, pose):
    frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
    result = pose.process(frame_rgb)

    if not result.pose_landmarks:
        return None, result

    frame_points = []
    for landmark in result.pose_landmarks.landmark:
        frame_points.append([landmark.x, landmark.y, landmark.z, landmark.visibility])
    return np.asarray(frame_points, dtype=np.float32), result


def prepare_model_input(sequence_buffer, sequence_length, use_velocity):
    if not sequence_buffer:
        return None

    frames = list(sequence_buffer)
    if len(frames) < sequence_length:
        pad_frame = frames[0]
        frames = [pad_frame] * (sequence_length - len(frames)) + frames
    else:
        frames = frames[-sequence_length:]

    features = build_features(np.asarray(frames, dtype=np.float32), add_velocity=use_velocity)
    return torch.from_numpy(features).unsqueeze(0).float()


def predict_scores(model, model_input, device):
    with torch.no_grad():
        logits = model(model_input.to(device))
        probs = torch.softmax(logits, dim=1).squeeze(0).cpu().numpy()
    return probs


def compute_pullup_bias(sequence_buffer):
    if len(sequence_buffer) < 12:
        return 0.0

    recent_frames = np.asarray(list(sequence_buffer)[-24:], dtype=np.float32)
    vis = recent_frames[:, :, 3]

    tracked_indices = [
        LEFT_SHOULDER,
        RIGHT_SHOULDER,
        LEFT_ELBOW,
        RIGHT_ELBOW,
        LEFT_WRIST,
        RIGHT_WRIST,
        LEFT_HIP,
        RIGHT_HIP,
    ]
    tracked_visibility = float(np.mean(vis[:, tracked_indices]))
    if tracked_visibility < 0.45:
        return 0.0

    shoulders = recent_frames[:, [LEFT_SHOULDER, RIGHT_SHOULDER], :2]
    elbows = recent_frames[:, [LEFT_ELBOW, RIGHT_ELBOW], :2]
    wrists = recent_frames[:, [LEFT_WRIST, RIGHT_WRIST], :2]
    hips = recent_frames[:, [LEFT_HIP, RIGHT_HIP], :2]
    ankles = recent_frames[:, [LEFT_ANKLE, RIGHT_ANKLE], :2]

    shoulder_center = shoulders.mean(axis=1)
    hip_center = hips.mean(axis=1)
    shoulder_width = np.linalg.norm(shoulders[:, 0] - shoulders[:, 1], axis=1)
    scale = np.maximum(shoulder_width, 1e-3)

    wrists_above_shoulders = np.mean(
        (wrists[:, 0, 1] < shoulders[:, 0, 1] - 0.02)
        & (wrists[:, 1, 1] < shoulders[:, 1, 1] - 0.02)
    )
    wrist_height_symmetry = 1.0 - np.clip(np.mean(np.abs(wrists[:, 0, 1] - wrists[:, 1, 1])) / 0.18, 0.0, 1.0)
    elbow_height_symmetry = 1.0 - np.clip(np.mean(np.abs(elbows[:, 0, 1] - elbows[:, 1, 1])) / 0.16, 0.0, 1.0)
    torso_alignment = 1.0 - np.clip(np.mean(np.abs(shoulder_center[:, 0] - hip_center[:, 0]) / scale), 0.0, 1.0)
    lateral_motion = np.std(hip_center[:, 0]) + np.std(shoulder_center[:, 0])
    low_lateral_motion = 1.0 - np.clip(lateral_motion / 0.08, 0.0, 1.0)
    ankle_width = np.mean(np.abs(ankles[:, 0, 0] - ankles[:, 1, 0]) / scale)
    compact_lower_body = 1.0 - np.clip((ankle_width - 0.8) / 1.2, 0.0, 1.0)

    bias = (
        0.30 * wrists_above_shoulders
        + 0.20 * wrist_height_symmetry
        + 0.18 * elbow_height_symmetry
        + 0.17 * torso_alignment
        + 0.10 * low_lateral_motion
        + 0.05 * compact_lower_body
    )
    return float(np.clip(bias, 0.0, 1.0))


def compute_rope_climb_bias(sequence_buffer):
    if len(sequence_buffer) < 12:
        return 0.0

    recent_frames = np.asarray(list(sequence_buffer)[-24:], dtype=np.float32)
    vis = recent_frames[:, :, 3]
    tracked_indices = [
        LEFT_SHOULDER,
        RIGHT_SHOULDER,
        LEFT_ELBOW,
        RIGHT_ELBOW,
        LEFT_WRIST,
        RIGHT_WRIST,
        LEFT_HIP,
        RIGHT_HIP,
        LEFT_ANKLE,
        RIGHT_ANKLE,
    ]
    tracked_visibility = float(np.mean(vis[:, tracked_indices]))
    if tracked_visibility < 0.42:
        return 0.0

    shoulders = recent_frames[:, [LEFT_SHOULDER, RIGHT_SHOULDER], :2]
    wrists = recent_frames[:, [LEFT_WRIST, RIGHT_WRIST], :2]
    hips = recent_frames[:, [LEFT_HIP, RIGHT_HIP], :2]
    ankles = recent_frames[:, [LEFT_ANKLE, RIGHT_ANKLE], :2]

    shoulder_center = shoulders.mean(axis=1)
    hip_center = hips.mean(axis=1)
    shoulder_width = np.linalg.norm(shoulders[:, 0] - shoulders[:, 1], axis=1)
    scale = np.maximum(shoulder_width, 1e-3)

    single_arm_offset = np.mean(np.abs(wrists[:, 0, 1] - wrists[:, 1, 1]) / scale)
    strong_arm_stagger = np.clip(single_arm_offset / 1.0, 0.0, 1.0)
    torso_alignment = 1.0 - np.clip(np.mean(np.abs(shoulder_center[:, 0] - hip_center[:, 0]) / scale), 0.0, 1.0)
    ankle_stagger = np.mean(np.abs(ankles[:, 0, 1] - ankles[:, 1, 1]) / scale)
    lower_body_offset = np.clip(ankle_stagger / 0.9, 0.0, 1.0)
    hip_vertical_motion = np.std(hip_center[:, 1]) / np.mean(scale)
    active_vertical_motion = np.clip(hip_vertical_motion / 0.55, 0.0, 1.0)
    side_motion = np.std(hip_center[:, 0]) / np.mean(scale)
    low_side_motion = 1.0 - np.clip(side_motion / 0.3, 0.0, 1.0)

    bias = (
        0.34 * strong_arm_stagger
        + 0.22 * torso_alignment
        + 0.16 * lower_body_offset
        + 0.16 * active_vertical_motion
        + 0.12 * low_side_motion
    )
    return float(np.clip(bias, 0.0, 1.0))


def compute_rock_climb_bias(sequence_buffer):
    if len(sequence_buffer) < 12:
        return 0.0

    recent_frames = np.asarray(list(sequence_buffer)[-24:], dtype=np.float32)
    vis = recent_frames[:, :, 3]
    tracked_indices = [
        LEFT_SHOULDER,
        RIGHT_SHOULDER,
        LEFT_ELBOW,
        RIGHT_ELBOW,
        LEFT_WRIST,
        RIGHT_WRIST,
        LEFT_HIP,
        RIGHT_HIP,
        LEFT_ANKLE,
        RIGHT_ANKLE,
    ]
    tracked_visibility = float(np.mean(vis[:, tracked_indices]))
    if tracked_visibility < 0.42:
        return 0.0

    shoulders = recent_frames[:, [LEFT_SHOULDER, RIGHT_SHOULDER], :2]
    wrists = recent_frames[:, [LEFT_WRIST, RIGHT_WRIST], :2]
    hips = recent_frames[:, [LEFT_HIP, RIGHT_HIP], :2]
    ankles = recent_frames[:, [LEFT_ANKLE, RIGHT_ANKLE], :2]

    shoulder_center = shoulders.mean(axis=1)
    hip_center = hips.mean(axis=1)
    shoulder_width = np.linalg.norm(shoulders[:, 0] - shoulders[:, 1], axis=1)
    scale = np.maximum(shoulder_width, 1e-3)

    wrist_height_gap = np.mean(np.abs(wrists[:, 0, 1] - wrists[:, 1, 1]) / scale)
    arm_asymmetry = np.clip(wrist_height_gap / 0.9, 0.0, 1.0)
    ankle_spread = np.mean(np.abs(ankles[:, 0, 0] - ankles[:, 1, 0]) / scale)
    wide_lower_body = np.clip((ankle_spread - 0.7) / 1.0, 0.0, 1.0)
    torso_side_shift = np.std(hip_center[:, 0]) + np.std(shoulder_center[:, 0])
    lateral_travel = np.clip(torso_side_shift / 0.16, 0.0, 1.0)
    torso_tilt = np.mean(np.abs(shoulder_center[:, 0] - hip_center[:, 0]) / scale)
    off_center_torso = np.clip(torso_tilt / 0.45, 0.0, 1.0)

    bias = (
        0.30 * arm_asymmetry
        + 0.26 * lateral_travel
        + 0.22 * wide_lower_body
        + 0.22 * off_center_torso
    )
    return float(np.clip(bias, 0.0, 1.0))


def compute_bench_press_bias(sequence_buffer):
    if len(sequence_buffer) < 12:
        return 0.0

    recent_frames = np.asarray(list(sequence_buffer)[-24:], dtype=np.float32)
    vis = recent_frames[:, :, 3]
    tracked_indices = [
        LEFT_SHOULDER,
        RIGHT_SHOULDER,
        LEFT_ELBOW,
        RIGHT_ELBOW,
        LEFT_WRIST,
        RIGHT_WRIST,
        LEFT_HIP,
        RIGHT_HIP,
        LEFT_ANKLE,
        RIGHT_ANKLE,
    ]
    tracked_visibility = float(np.mean(vis[:, tracked_indices]))
    if tracked_visibility < 0.40:
        return 0.0

    shoulders = recent_frames[:, [LEFT_SHOULDER, RIGHT_SHOULDER], :2]
    elbows = recent_frames[:, [LEFT_ELBOW, RIGHT_ELBOW], :2]
    wrists = recent_frames[:, [LEFT_WRIST, RIGHT_WRIST], :2]
    hips = recent_frames[:, [LEFT_HIP, RIGHT_HIP], :2]
    ankles = recent_frames[:, [LEFT_ANKLE, RIGHT_ANKLE], :2]

    shoulder_center = shoulders.mean(axis=1)
    hip_center = hips.mean(axis=1)
    ankle_center = ankles.mean(axis=1)
    shoulder_width = np.linalg.norm(shoulders[:, 0] - shoulders[:, 1], axis=1)
    scale = np.maximum(shoulder_width, 1e-3)

    torso_dx = np.abs(hip_center[:, 0] - shoulder_center[:, 0])
    torso_dy = np.abs(hip_center[:, 1] - shoulder_center[:, 1])
    body_dx = np.abs(ankle_center[:, 0] - shoulder_center[:, 0])
    body_dy = np.abs(ankle_center[:, 1] - shoulder_center[:, 1])

    torso_horizontal = np.mean(torso_dx / np.maximum(torso_dx + torso_dy, 1e-3))
    body_horizontal = np.mean(body_dx / np.maximum(body_dx + body_dy, 1e-3))
    wrists_above_shoulders = np.mean(
        (wrists[:, 0, 1] < shoulders[:, 0, 1] - 0.02)
        & (wrists[:, 1, 1] < shoulders[:, 1, 1] - 0.02)
    )
    wrist_height_symmetry = 1.0 - np.clip(np.mean(np.abs(wrists[:, 0, 1] - wrists[:, 1, 1])) / 0.18, 0.0, 1.0)
    elbow_height_symmetry = 1.0 - np.clip(np.mean(np.abs(elbows[:, 0, 1] - elbows[:, 1, 1])) / 0.18, 0.0, 1.0)
    hip_vertical_motion = np.std(hip_center[:, 1]) / np.maximum(np.mean(scale), 1e-3)
    low_vertical_motion = 1.0 - np.clip(hip_vertical_motion / 0.24, 0.0, 1.0)
    ankles_below_hips = np.mean(ankle_center[:, 1] > hip_center[:, 1] + 0.04)

    bias = (
        0.28 * torso_horizontal
        + 0.26 * body_horizontal
        + 0.18 * wrists_above_shoulders
        + 0.12 * wrist_height_symmetry
        + 0.08 * elbow_height_symmetry
        + 0.05 * low_vertical_motion
        + 0.03 * ankles_below_hips
    )
    return float(np.clip(bias, 0.0, 1.0))


def compute_high_jump_bias(sequence_buffer):
    if len(sequence_buffer) < 12:
        return 0.0

    recent_frames = np.asarray(list(sequence_buffer)[-24:], dtype=np.float32)
    vis = recent_frames[:, :, 3]
    tracked_indices = [
        LEFT_SHOULDER,
        RIGHT_SHOULDER,
        LEFT_WRIST,
        RIGHT_WRIST,
        LEFT_HIP,
        RIGHT_HIP,
        LEFT_ANKLE,
        RIGHT_ANKLE,
    ]
    tracked_visibility = float(np.mean(vis[:, tracked_indices]))
    if tracked_visibility < 0.38:
        return 0.0

    shoulders = recent_frames[:, [LEFT_SHOULDER, RIGHT_SHOULDER], :2]
    wrists = recent_frames[:, [LEFT_WRIST, RIGHT_WRIST], :2]
    hips = recent_frames[:, [LEFT_HIP, RIGHT_HIP], :2]
    ankles = recent_frames[:, [LEFT_ANKLE, RIGHT_ANKLE], :2]

    shoulder_center = shoulders.mean(axis=1)
    hip_center = hips.mean(axis=1)
    ankle_center = ankles.mean(axis=1)
    shoulder_width = np.linalg.norm(shoulders[:, 0] - shoulders[:, 1], axis=1)
    scale = np.maximum(shoulder_width, 1e-3)

    torso_dx = np.abs(hip_center[:, 0] - shoulder_center[:, 0])
    torso_dy = np.abs(hip_center[:, 1] - shoulder_center[:, 1])
    body_dx = np.abs(ankle_center[:, 0] - shoulder_center[:, 0])
    body_dy = np.abs(ankle_center[:, 1] - shoulder_center[:, 1])

    torso_vertical = np.mean(torso_dy / np.maximum(torso_dx + torso_dy, 1e-3))
    body_vertical = np.mean(body_dy / np.maximum(body_dx + body_dy, 1e-3))
    active_vertical_motion = np.clip(
        (np.std(hip_center[:, 1]) + np.std(shoulder_center[:, 1])) / np.maximum(np.mean(scale), 1e-3) / 0.32,
        0.0,
        1.0,
    )
    overhead_reach = np.mean(
        (wrists[:, 0, 1] < shoulders[:, 0, 1] - 0.03)
        | (wrists[:, 1, 1] < shoulders[:, 1, 1] - 0.03)
    )

    bias = (
        0.34 * torso_vertical
        + 0.34 * body_vertical
        + 0.20 * active_vertical_motion
        + 0.12 * overhead_reach
    )
    return float(np.clip(bias, 0.0, 1.0))


def compute_still_rings_bias(sequence_buffer):
    if len(sequence_buffer) < 12:
        return 0.0

    recent_frames = np.asarray(list(sequence_buffer)[-24:], dtype=np.float32)
    vis = recent_frames[:, :, 3]
    tracked_indices = [
        LEFT_SHOULDER,
        RIGHT_SHOULDER,
        LEFT_ELBOW,
        RIGHT_ELBOW,
        LEFT_WRIST,
        RIGHT_WRIST,
        LEFT_HIP,
        RIGHT_HIP,
        LEFT_ANKLE,
        RIGHT_ANKLE,
    ]
    tracked_visibility = float(np.mean(vis[:, tracked_indices]))
    if tracked_visibility < 0.40:
        return 0.0

    shoulders = recent_frames[:, [LEFT_SHOULDER, RIGHT_SHOULDER], :2]
    elbows = recent_frames[:, [LEFT_ELBOW, RIGHT_ELBOW], :2]
    wrists = recent_frames[:, [LEFT_WRIST, RIGHT_WRIST], :2]
    hips = recent_frames[:, [LEFT_HIP, RIGHT_HIP], :2]
    ankles = recent_frames[:, [LEFT_ANKLE, RIGHT_ANKLE], :2]

    shoulder_center = shoulders.mean(axis=1)
    hip_center = hips.mean(axis=1)
    ankle_center = ankles.mean(axis=1)
    shoulder_width = np.linalg.norm(shoulders[:, 0] - shoulders[:, 1], axis=1)
    scale = np.maximum(shoulder_width, 1e-3)

    torso_dx = np.abs(hip_center[:, 0] - shoulder_center[:, 0])
    torso_dy = np.abs(hip_center[:, 1] - shoulder_center[:, 1])
    body_dx = np.abs(ankle_center[:, 0] - shoulder_center[:, 0])
    body_dy = np.abs(ankle_center[:, 1] - shoulder_center[:, 1])

    torso_vertical = np.mean(torso_dy / np.maximum(torso_dx + torso_dy, 1e-3))
    body_vertical = np.mean(body_dy / np.maximum(body_dx + body_dy, 1e-3))
    wrists_above_shoulders = np.mean(
        (wrists[:, 0, 1] < shoulders[:, 0, 1] - 0.02)
        & (wrists[:, 1, 1] < shoulders[:, 1, 1] - 0.02)
    )
    arm_stretch = np.mean(
        (
            np.linalg.norm(wrists[:, 0] - shoulders[:, 0], axis=1)
            + np.linalg.norm(wrists[:, 1] - shoulders[:, 1], axis=1)
        )
        / (2.0 * scale)
    )
    extended_arms = np.clip((arm_stretch - 0.85) / 0.55, 0.0, 1.0)
    elbow_width = np.mean(np.abs(elbows[:, 0, 0] - elbows[:, 1, 0]) / scale)
    wide_arms = np.clip((elbow_width - 1.0) / 0.9, 0.0, 1.0)
    body_swing = (np.std(hip_center[:, 0]) + np.std(shoulder_center[:, 0])) / np.maximum(np.mean(scale), 1e-3)
    low_body_swing = 1.0 - np.clip(body_swing / 0.26, 0.0, 1.0)

    bias = (
        0.28 * torso_vertical
        + 0.24 * body_vertical
        + 0.20 * wrists_above_shoulders
        + 0.16 * extended_arms
        + 0.08 * wide_arms
        + 0.04 * low_body_swing
    )
    return float(np.clip(bias, 0.0, 1.0))


def compute_lunges_bias(sequence_buffer):
    if len(sequence_buffer) < 12:
        return 0.0

    recent_frames = np.asarray(list(sequence_buffer)[-24:], dtype=np.float32)
    vis = recent_frames[:, :, 3]
    tracked_indices = [
        LEFT_SHOULDER,
        RIGHT_SHOULDER,
        LEFT_HIP,
        RIGHT_HIP,
        LEFT_ANKLE,
        RIGHT_ANKLE,
    ]
    tracked_visibility = float(np.mean(vis[:, tracked_indices]))
    if tracked_visibility < 0.40:
        return 0.0

    shoulders = recent_frames[:, [LEFT_SHOULDER, RIGHT_SHOULDER], :2]
    hips = recent_frames[:, [LEFT_HIP, RIGHT_HIP], :2]
    ankles = recent_frames[:, [LEFT_ANKLE, RIGHT_ANKLE], :2]

    shoulder_center = shoulders.mean(axis=1)
    hip_center = hips.mean(axis=1)
    shoulder_width = np.linalg.norm(shoulders[:, 0] - shoulders[:, 1], axis=1)
    scale = np.maximum(shoulder_width, 1e-3)

    ankle_x_gap = np.mean(np.abs(ankles[:, 0, 0] - ankles[:, 1, 0]) / scale)
    split_stance = np.clip((ankle_x_gap - 0.7) / 1.1, 0.0, 1.0)
    ankle_y_gap = np.mean(np.abs(ankles[:, 0, 1] - ankles[:, 1, 1]) / scale)
    front_back_offset = np.clip(ankle_y_gap / 0.65, 0.0, 1.0)
    torso_alignment = 1.0 - np.clip(np.mean(np.abs(shoulder_center[:, 0] - hip_center[:, 0]) / scale), 0.0, 1.0)
    low_side_motion = 1.0 - np.clip(np.std(hip_center[:, 0]) / np.maximum(np.mean(scale), 1e-3) / 0.26, 0.0, 1.0)
    torso_vertical = np.mean(
        np.abs(hip_center[:, 1] - shoulder_center[:, 1])
        / np.maximum(
            np.abs(hip_center[:, 0] - shoulder_center[:, 0]) + np.abs(hip_center[:, 1] - shoulder_center[:, 1]),
            1e-3,
        )
    )

    bias = (
        0.34 * split_stance
        + 0.24 * front_back_offset
        + 0.18 * torso_alignment
        + 0.14 * low_side_motion
        + 0.10 * torso_vertical
    )
    return float(np.clip(bias, 0.0, 1.0))


def force_class_probability(scores, target_idx, desired_prob):
    adjusted_scores = scores.astype(np.float32).copy()
    desired_prob = float(np.clip(desired_prob, 0.40, 0.90))
    current_prob = float(adjusted_scores[target_idx])
    if current_prob >= desired_prob:
        return adjusted_scores / np.clip(np.sum(adjusted_scores), 1e-6, None)

    other_total = float(np.sum(adjusted_scores) - current_prob)
    if other_total <= 1e-6:
        adjusted_scores[target_idx] = 1.0
        return adjusted_scores

    scale = (1.0 - desired_prob) / other_total
    for idx in range(len(adjusted_scores)):
        if idx == target_idx:
            continue
        adjusted_scores[idx] *= scale
    adjusted_scores[target_idx] = desired_prob
    adjusted_scores /= np.clip(np.sum(adjusted_scores), 1e-6, None)
    return adjusted_scores


def apply_benchpress_highjump_heuristic(scores, class_names, sequence_buffer):
    if scores is None:
        return scores

    try:
        bench_idx = class_names.index("BenchPress")
        jump_idx = class_names.index("HighJump")
    except ValueError:
        return scores

    adjusted_scores = scores.astype(np.float32).copy()
    bench_score = float(adjusted_scores[bench_idx])
    jump_score = float(adjusted_scores[jump_idx])
    bench_bias = compute_bench_press_bias(sequence_buffer)
    high_jump_bias = compute_high_jump_bias(sequence_buffer)

    top_pair_score = max(bench_score, jump_score)
    if top_pair_score < 0.14:
        return adjusted_scores

    # 卧推纠偏: 人体明显横躺且双手在胸前上方时，不应被跳高抢占第一名
    hard_benchpress_display = (
        bench_bias >= 0.58
        and bench_bias >= high_jump_bias + 0.12
        and jump_score >= 0.12
        and (bench_score + jump_score) >= 0.22
    )
    if hard_benchpress_display:
        desired_prob = max(0.62, jump_score + 0.16, bench_score + 0.12)
        return force_class_probability(adjusted_scores, bench_idx, desired_prob)

    if jump_score > bench_score and bench_bias >= 0.50 and bench_bias >= high_jump_bias + 0.06:
        transfer = min(jump_score * 0.52, 0.18 * bench_bias, jump_score - bench_score + 0.06)
        adjusted_scores[bench_idx] += transfer
        adjusted_scores[jump_idx] = max(0.0, adjusted_scores[jump_idx] - transfer)

    adjusted_scores /= np.clip(np.sum(adjusted_scores), 1e-6, None)
    return adjusted_scores


def apply_benchpress_stillrings_heuristic(scores, class_names, sequence_buffer):
    if scores is None:
        return scores

    try:
        bench_idx = class_names.index("BenchPress")
        rings_idx = class_names.index("StillRings")
    except ValueError:
        return scores

    adjusted_scores = scores.astype(np.float32).copy()
    bench_score = float(adjusted_scores[bench_idx])
    rings_score = float(adjusted_scores[rings_idx])
    bench_bias = compute_bench_press_bias(sequence_buffer)
    rings_bias = compute_still_rings_bias(sequence_buffer)

    top_pair_score = max(bench_score, rings_score)
    if top_pair_score < 0.14:
        return adjusted_scores

    # 卧推硬覆盖: 当画面明显是横躺推举，而模型把它看成吊环时，直接显示为卧推
    hard_benchpress_display = (
        bench_bias >= 0.56
        and bench_bias >= rings_bias + 0.12
        and rings_score >= 0.12
        and (bench_score + rings_score) >= 0.22
    )
    if hard_benchpress_display:
        desired_prob = max(0.64, rings_score + 0.18, bench_score + 0.14)
        return force_class_probability(adjusted_scores, bench_idx, desired_prob)

    if rings_score > bench_score and bench_bias >= 0.50 and bench_bias >= rings_bias + 0.06:
        transfer = min(rings_score * 0.56, 0.20 * bench_bias, rings_score - bench_score + 0.06)
        adjusted_scores[bench_idx] += transfer
        adjusted_scores[rings_idx] = max(0.0, adjusted_scores[rings_idx] - transfer)

    adjusted_scores /= np.clip(np.sum(adjusted_scores), 1e-6, None)
    return adjusted_scores


def apply_ropeclimbing_lunges_heuristic(scores, class_names, sequence_buffer):
    if scores is None:
        return scores

    try:
        rope_idx = class_names.index("RopeClimbing")
        lunges_idx = class_names.index("Lunges")
    except ValueError:
        return scores

    adjusted_scores = scores.astype(np.float32).copy()
    rope_score = float(adjusted_scores[rope_idx])
    lunges_score = float(adjusted_scores[lunges_idx])
    rope_bias = compute_rope_climb_bias(sequence_buffer)
    lunges_bias = compute_lunges_bias(sequence_buffer)

    top_pair_score = max(rope_score, lunges_score)
    if top_pair_score < 0.14:
        return adjusted_scores

    # 弓步蹲硬覆盖: 下肢明显前后分腿、躯干稳定时，不应被爬绳抢占第一名
    hard_lunges_display = (
        lunges_bias >= 0.54
        and lunges_bias >= rope_bias + 0.10
        and rope_score >= 0.12
        and (lunges_score + rope_score) >= 0.22
    )
    if hard_lunges_display:
        desired_prob = max(0.62, rope_score + 0.16, lunges_score + 0.12)
        return force_class_probability(adjusted_scores, lunges_idx, desired_prob)

    if rope_score > lunges_score and lunges_bias >= 0.48 and lunges_bias >= rope_bias + 0.06:
        transfer = min(rope_score * 0.52, 0.18 * lunges_bias, rope_score - lunges_score + 0.05)
        adjusted_scores[lunges_idx] += transfer
        adjusted_scores[rope_idx] = max(0.0, adjusted_scores[rope_idx] - transfer)

    adjusted_scores /= np.clip(np.sum(adjusted_scores), 1e-6, None)
    return adjusted_scores


def apply_upper_body_climb_heuristic(scores, class_names, sequence_buffer):
    if scores is None:
        return scores

    try:
        pull_idx = class_names.index("PullUps")
        climb_idx = class_names.index("RockClimbingIndoor")
        rope_idx = class_names.index("RopeClimbing")
    except ValueError:
        return scores

    adjusted_scores = scores.astype(np.float32).copy()
    pull_score = float(adjusted_scores[pull_idx])
    climb_score = float(adjusted_scores[climb_idx])
    pullup_bias = compute_pullup_bias(sequence_buffer)
    rope_bias = compute_rope_climb_bias(sequence_buffer)
    rock_bias = compute_rock_climb_bias(sequence_buffer)

    top_group_score = max(pull_score, climb_score, float(adjusted_scores[rope_idx]))
    if top_group_score < 0.16:
        return adjusted_scores

    # 引体向上硬覆盖: 满足这组形态条件时，直接显示为引体向上
    hard_pullup_display = (
        pullup_bias >= 0.54
        and pullup_bias >= rock_bias + 0.03
        and pullup_bias >= rope_bias + 0.02
        and climb_score >= 0.14
        and (pull_score + climb_score) >= 0.26
    )
    if hard_pullup_display:
        desired_prob = max(0.64, climb_score + 0.18, pull_score + 0.14)
        return force_class_probability(adjusted_scores, pull_idx, desired_prob)

    # 强规则优先: 典型引体向上形态时，直接优先压过室内攀岩
    pullup_override = (
        pullup_bias >= 0.74
        and rock_bias <= 0.60
        and rope_bias <= 0.68
        and climb_score >= 0.18
        and (climb_score - pull_score) <= 0.32
    )
    if pullup_override:
        desired_pull = max(pull_score, climb_score + 0.08, 0.42)
        transfer = min(climb_score * 0.60, desired_pull - pull_score)
        if transfer > 0:
            adjusted_scores[pull_idx] += transfer
            adjusted_scores[climb_idx] = max(0.0, adjusted_scores[climb_idx] - transfer)

    if climb_score > pull_score:
        gap = climb_score - pull_score
        if 0.0 < gap <= 0.22 and pull_score >= 0.10 and pullup_bias >= 0.62:
            transfer = min(climb_score * 0.35, 0.18 * pullup_bias, gap + 0.02)
            adjusted_scores[pull_idx] += transfer
            adjusted_scores[climb_idx] = max(0.0, adjusted_scores[climb_idx] - transfer)

    rope_score = float(adjusted_scores[rope_idx])
    leader_idx = int(np.argmax([float(adjusted_scores[pull_idx]), float(adjusted_scores[rope_idx]), float(adjusted_scores[climb_idx])]))
    leader_names = ["PullUps", "RopeClimbing", "RockClimbingIndoor"]
    leader_name = leader_names[leader_idx]

    if leader_name != "RopeClimbing" and rope_score >= 0.08 and rope_bias >= 0.60:
        target_idx = pull_idx if adjusted_scores[pull_idx] >= adjusted_scores[climb_idx] else climb_idx
        gap = float(adjusted_scores[target_idx] - adjusted_scores[rope_idx])
        if gap <= 0.24:
            transfer = min(float(adjusted_scores[target_idx]) * 0.22, 0.15 * rope_bias, gap + 0.01)
            adjusted_scores[rope_idx] += transfer
            adjusted_scores[target_idx] = max(0.0, adjusted_scores[target_idx] - transfer)

    rope_score = float(adjusted_scores[rope_idx])
    pull_score = float(adjusted_scores[pull_idx])
    climb_score = float(adjusted_scores[climb_idx])

    # 次强规则: 引体向上特征明显强于攀岩时，继续追加一次偏置
    if (
        pullup_bias >= 0.68
        and (pullup_bias - rock_bias) >= 0.12
        and climb_score > pull_score
        and climb_score <= 0.52
    ):
        transfer = min(climb_score * 0.28, 0.12 + 0.10 * (pullup_bias - rock_bias), climb_score - pull_score + 0.04)
        adjusted_scores[pull_idx] += transfer
        adjusted_scores[climb_idx] = max(0.0, adjusted_scores[climb_idx] - transfer)

    rope_score = float(adjusted_scores[rope_idx])
    pull_score = float(adjusted_scores[pull_idx])
    climb_score = float(adjusted_scores[climb_idx])
    if climb_score < max(pull_score, rope_score) and rock_bias >= 0.58:
        target_idx = pull_idx if pull_score >= rope_score else rope_idx
        gap = float(adjusted_scores[target_idx] - adjusted_scores[climb_idx])
        if gap <= 0.20 and adjusted_scores[climb_idx] >= 0.10:
            transfer = min(float(adjusted_scores[target_idx]) * 0.20, 0.14 * rock_bias, gap + 0.01)
            adjusted_scores[climb_idx] += transfer
            adjusted_scores[target_idx] = max(0.0, adjusted_scores[target_idx] - transfer)

    # 最后一层硬重排: 只在上肢三类里判断，如果整体信号更像引体向上，就强制把它提到第一
    rope_score = float(adjusted_scores[rope_idx])
    pull_score = float(adjusted_scores[pull_idx])
    climb_score = float(adjusted_scores[climb_idx])
    pull_priority = pull_score + 0.62 * pullup_bias - 0.20 * rock_bias - 0.08 * rope_bias
    climb_priority = climb_score + 0.38 * rock_bias - 0.18 * pullup_bias
    rope_priority = rope_score + 0.42 * rope_bias - 0.10 * pullup_bias
    hard_pullup_override = (
        pullup_bias >= 0.58
        and pull_priority >= climb_priority - 0.02
        and pull_priority >= rope_priority - 0.04
        and climb_score >= 0.14
    )
    if hard_pullup_override:
        desired_pull = max(pull_score, climb_score + 0.14, rope_score + 0.08, 0.48)
        remaining = desired_pull - pull_score
        if remaining > 0:
            from_climb = min(remaining, float(adjusted_scores[climb_idx]) * 0.82)
            adjusted_scores[pull_idx] += from_climb
            adjusted_scores[climb_idx] = max(0.0, adjusted_scores[climb_idx] - from_climb)
            remaining -= from_climb
        if remaining > 0:
            from_rope = min(remaining, float(adjusted_scores[rope_idx]) * 0.40)
            adjusted_scores[pull_idx] += from_rope
            adjusted_scores[rope_idx] = max(0.0, adjusted_scores[rope_idx] - from_rope)

    adjusted_scores /= np.clip(np.sum(adjusted_scores), 1e-6, None)
    return adjusted_scores


def load_chinese_font(font_size):
    if ImageFont is None:
        return None

    return {
        "zh": load_font_from_candidates(FONT_CANDIDATES, font_size),
        "latin": load_font_from_candidates(TECH_FONT_CANDIDATES, font_size),
    }


def load_tech_font(font_size):
    if ImageFont is None:
        return None

    return {
        "zh": load_font_from_candidates(FONT_CANDIDATES, font_size),
        "latin": load_font_from_candidates(TECH_FONT_CANDIDATES, font_size),
    }


def load_font_from_candidates(candidates, font_size):
    if ImageFont is None:
        return None

    for font_path in candidates:
        if os.path.exists(font_path):
            return ImageFont.truetype(font_path, font_size)
    return None


def is_font_bundle(font):
    return isinstance(font, dict)


def is_cjk_char(char):
    code = ord(char)
    return (
        0x4E00 <= code <= 0x9FFF
        or 0x3400 <= code <= 0x4DBF
        or 0x3000 <= code <= 0x303F
        or 0xFF00 <= code <= 0xFFEF
    )


def get_font_for_char(font, char):
    if not is_font_bundle(font):
        return font

    prefer_zh = is_cjk_char(char)
    if prefer_zh:
        return font.get("zh") or font.get("latin")
    return font.get("latin") or font.get("zh")


def split_text_runs(text, font):
    if not is_font_bundle(font):
        return [(text, font)]

    runs = []
    current_text = ""
    current_font = None
    for char in text:
        char_font = get_font_for_char(font, char)
        if current_font is None or char_font == current_font:
            current_text += char
            current_font = char_font
            continue
        runs.append((current_text, current_font))
        current_text = char
        current_font = char_font
    if current_text:
        runs.append((current_text, current_font))
    return runs


def get_font_vertical_metrics(font, sample_text="Ag09中"):
    if font is None:
        return 0, 0

    if hasattr(font, "getmetrics"):
        ascent, descent = font.getmetrics()
        return max(0, int(ascent)), max(0, int(descent))

    bbox = font.getbbox(sample_text)
    ascent = max(0, -int(bbox[1]))
    descent = max(0, int(bbox[3]))
    return ascent, descent


def fit_chinese_font_to_width(text, max_width, preferred_size, min_size=18, max_size=72):
    if ImageFont is None:
        return None

    max_width = max(1, int(max_width))
    start_size = max(int(preferred_size), int(min_size))
    upper_size = max(start_size, int(max_size))

    best_font = None
    for font_size in range(upper_size, int(min_size) - 1, -1):
        candidate = load_chinese_font(font_size)
        if candidate is None:
            return None
        if measure_text_width(text, candidate) <= max_width:
            return candidate
        best_font = candidate
    return best_font


def fit_tech_font_to_width(text, max_width, preferred_size, min_size=18, max_size=72):
    if ImageFont is None:
        return None

    max_width = max(1, int(max_width))
    start_size = max(int(preferred_size), int(min_size))
    upper_size = max(start_size, int(max_size))

    best_font = None
    for font_size in range(upper_size, int(min_size) - 1, -1):
        candidate = load_tech_font(font_size)
        if candidate is None:
            return None
        if measure_text_width(text, candidate) <= max_width:
            return candidate
        best_font = candidate
    return best_font


def scaled_font_size(base_size, ui_scale):
    return max(14, int(round(base_size * ui_scale)))


def draw_text(frame, text, position, color, font, fallback_scale=0.6, fallback_thickness=2):
    if (font is None or (is_font_bundle(font) and not font.get("zh") and not font.get("latin")) or Image is None):
        safe_text = text.encode("ascii", errors="ignore").decode("ascii") or "N/A"
        shadow_layers = [
            (4, 4, (8, 10, 14), max(6, fallback_thickness + 5)),
            (2, 2, (0, 0, 0), max(5, fallback_thickness + 4)),
        ]
        for dx, dy, shadow_color, shadow_thickness in shadow_layers:
            cv2.putText(
                frame,
                safe_text,
                (position[0] + dx, position[1] + dy),
                cv2.FONT_HERSHEY_SIMPLEX,
                fallback_scale,
                shadow_color,
                shadow_thickness,
                lineType=cv2.LINE_AA,
            )
        stroke_offsets = [
            (-3, 0), (3, 0), (0, -3), (0, 3),
            (-2, 0), (2, 0), (0, -2), (0, 2),
            (-1, -1), (1, -1), (-1, 1), (1, 1),
            (-2, -2), (2, -2), (-2, 2), (2, 2),
            (-3, -1), (3, -1), (-3, 1), (3, 1),
            (-1, -3), (1, -3), (-1, 3), (1, 3),
        ]
        for dx, dy in stroke_offsets:
            cv2.putText(
                frame,
                safe_text,
                (position[0] + dx, position[1] + dy),
                cv2.FONT_HERSHEY_SIMPLEX,
                fallback_scale,
                (0, 0, 0),
                max(5, fallback_thickness + 4),
                lineType=cv2.LINE_AA,
            )
        cv2.putText(
            frame,
            safe_text,
            position,
            cv2.FONT_HERSHEY_SIMPLEX,
            fallback_scale,
            color,
            fallback_thickness,
            lineType=cv2.LINE_AA,
        )
        return

    frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    pil_image = Image.fromarray(frame_rgb)
    drawer = ImageDraw.Draw(pil_image)
    text_runs = split_text_runs(text, font)
    run_metrics = [get_font_vertical_metrics(run_font, run_text) for run_text, run_font in text_runs]
    common_ascent = max((ascent for ascent, _ in run_metrics), default=0)
    shadow_specs = [
        ((4, 4), (10, 14, 20), 6),
        ((2, 2), (0, 0, 0), 5),
    ]
    for (dx, dy), shadow_color, stroke_width in shadow_specs:
        cursor_x = position[0] + dx
        for (run_text, run_font), (run_ascent, _) in zip(text_runs, run_metrics):
            run_y = position[1] + (common_ascent - run_ascent)
            drawer.text(
                (cursor_x, run_y + dy),
                run_text,
                font=run_font,
                fill=shadow_color,
                stroke_width=stroke_width,
                stroke_fill=(0, 0, 0),
            )
            cursor_x += measure_text_width(run_text, run_font, fallback_scale, fallback_thickness)
    cursor_x = position[0]
    for (run_text, run_font), (run_ascent, _) in zip(text_runs, run_metrics):
        run_y = position[1] + (common_ascent - run_ascent)
        drawer.text(
            (cursor_x, run_y),
            run_text,
            font=run_font,
            fill=(int(color[2]), int(color[1]), int(color[0])),
            stroke_width=4,
            stroke_fill=(0, 0, 0),
        )
        cursor_x += measure_text_width(run_text, run_font, fallback_scale, fallback_thickness)
    frame[:, :] = cv2.cvtColor(np.asarray(pil_image), cv2.COLOR_RGB2BGR)


def draw_text_crisp(frame, text, position, color, font, fallback_scale=0.6, fallback_thickness=2, stroke_width=1, stroke_fill=(255, 255, 255)):
    if (font is None or (is_font_bundle(font) and not font.get("zh") and not font.get("latin")) or Image is None):
        safe_text = text.encode("ascii", errors="ignore").decode("ascii") or "N/A"
        cv2.putText(
            frame,
            safe_text,
            position,
            cv2.FONT_HERSHEY_SIMPLEX,
            fallback_scale,
            stroke_fill,
            max(1, fallback_thickness + max(1, stroke_width)),
            lineType=cv2.LINE_AA,
        )
        cv2.putText(
            frame,
            safe_text,
            position,
            cv2.FONT_HERSHEY_SIMPLEX,
            fallback_scale,
            color,
            max(1, fallback_thickness),
            lineType=cv2.LINE_AA,
        )
        return

    frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    pil_image = Image.fromarray(frame_rgb)
    drawer = ImageDraw.Draw(pil_image)
    text_runs = split_text_runs(text, font)
    run_metrics = [get_font_vertical_metrics(run_font, run_text) for run_text, run_font in text_runs]
    common_ascent = max((ascent for ascent, _ in run_metrics), default=0)
    cursor_x = position[0]
    for (run_text, run_font), (run_ascent, _) in zip(text_runs, run_metrics):
        run_y = position[1] + (common_ascent - run_ascent)
        drawer.text(
            (cursor_x, run_y),
            run_text,
            font=run_font,
            fill=(int(color[2]), int(color[1]), int(color[0])),
            stroke_width=max(0, int(stroke_width)),
            stroke_fill=(int(stroke_fill[2]), int(stroke_fill[1]), int(stroke_fill[0])),
        )
        cursor_x += measure_text_width(run_text, run_font, fallback_scale, fallback_thickness)
    frame[:, :] = cv2.cvtColor(np.asarray(pil_image), cv2.COLOR_RGB2BGR)


def measure_text_width(text, font, fallback_scale=0.6, fallback_thickness=2):
    if (font is None or (is_font_bundle(font) and not font.get("zh") and not font.get("latin")) or Image is None):
        safe_text = text.encode("ascii", errors="ignore").decode("ascii") or "N/A"
        text_size, _ = cv2.getTextSize(
            safe_text,
            cv2.FONT_HERSHEY_SIMPLEX,
            fallback_scale,
            fallback_thickness,
        )
        return text_size[0]

    if is_font_bundle(font):
        return sum(measure_text_width(run_text, run_font, fallback_scale, fallback_thickness) for run_text, run_font in split_text_runs(text, font))

    bbox = font.getbbox(text)
    return max(0, bbox[2] - bbox[0])


def measure_text_height(text, font, fallback_scale=0.6, fallback_thickness=2):
    if (font is None or (is_font_bundle(font) and not font.get("zh") and not font.get("latin")) or Image is None):
        safe_text = text.encode("ascii", errors="ignore").decode("ascii") or "N/A"
        text_size, _ = cv2.getTextSize(
            safe_text,
            cv2.FONT_HERSHEY_SIMPLEX,
            fallback_scale,
            fallback_thickness,
        )
        return text_size[1]

    if is_font_bundle(font):
        run_metrics = [get_font_vertical_metrics(run_font, run_text) for run_text, run_font in split_text_runs(text, font)]
        if not run_metrics:
            return 0
        max_ascent = max(ascent for ascent, _ in run_metrics)
        max_descent = max(descent for _, descent in run_metrics)
        return max_ascent + max_descent

    bbox = font.getbbox(text)
    return max(0, bbox[3] - bbox[1])


def draw_text_backdrop(
    frame,
    text,
    position,
    font,
    fill_color,
    fallback_scale=0.6,
    fallback_thickness=2,
    padding_x=18,
    padding_y=10,
    radius=16,
    alpha=0.60,
):
    text_width = measure_text_width(text, font, fallback_scale, fallback_thickness)
    text_height = measure_text_height(text, font, fallback_scale, fallback_thickness)
    x1 = max(0, position[0] - padding_x)
    y1 = max(0, position[1] - padding_y)
    x2 = min(frame.shape[1], position[0] + text_width + padding_x)
    y2 = min(frame.shape[0], position[1] + text_height + padding_y)
    if x2 <= x1 or y2 <= y1:
        return

    overlay = frame.copy()
    draw_rounded_panel(overlay, (x1, y1), (x2, y2), fill_color, radius)
    cv2.addWeighted(overlay, alpha, frame, 1.0 - alpha, 0, frame)


def draw_pose(frame, pose_result, drawing_utils, drawing_styles, pose_module):
    if not pose_result.pose_landmarks:
        return
    landmarks = pose_result.pose_landmarks.landmark
    overlay = frame.copy()
    excluded_ids = set(range(0, 11))
    allowed_connections = [
        connection
        for connection in pose_module.POSE_CONNECTIONS
        if connection[0] not in excluded_ids and connection[1] not in excluded_ids
    ]
    line_color = rgb(146, 236, 255)
    point_color = rgb(255, 184, 96)
    frame_h, frame_w = frame.shape[:2]

    for start_idx, end_idx in allowed_connections:
        start = landmarks[start_idx]
        end = landmarks[end_idx]
        if min(start.visibility, end.visibility) < 0.55:
            continue
        start_xy = (int(start.x * frame_w), int(start.y * frame_h))
        end_xy = (int(end.x * frame_w), int(end.y * frame_h))
        cv2.line(overlay, start_xy, end_xy, line_color, 1, lineType=cv2.LINE_AA)

    for idx in [11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28]:
        if idx >= len(landmarks):
            continue
        landmark = landmarks[idx]
        if landmark.visibility < 0.6:
            continue
        center = (int(landmark.x * frame_w), int(landmark.y * frame_h))
        cv2.circle(overlay, center, 2, point_color, -1, lineType=cv2.LINE_AA)

    cv2.addWeighted(overlay, 0.34, frame, 0.66, 0, frame)


def get_screen_size():
    if tk is None:
        return 1920, 1080

    root = None
    try:
        root = tk.Tk()
        root.withdraw()
        return root.winfo_screenwidth(), root.winfo_screenheight()
    except Exception:
        return 1920, 1080
    finally:
        if root is not None:
            root.destroy()


def get_preview_window_size(frame_width, frame_height, screen_width, screen_height, margin=0.92):
    frame_width = max(1, int(frame_width))
    frame_height = max(1, int(frame_height))
    screen_width = max(1, int(screen_width))
    screen_height = max(1, int(screen_height))

    max_width = max(640, int(screen_width * margin))
    max_height = max(360, int(screen_height * margin))
    scale = min(max_width / frame_width, max_height / frame_height, 1.0)

    window_width = max(1, int(round(frame_width * scale)))
    window_height = max(1, int(round(frame_height * scale)))
    return window_width, window_height


def draw_rounded_panel(overlay, top_left, bottom_right, color, radius):
    x1, y1 = top_left
    x2, y2 = bottom_right
    radius = max(4, min(radius, (x2 - x1) // 2, (y2 - y1) // 2))

    cv2.rectangle(overlay, (x1 + radius, y1), (x2 - radius, y2), color, -1)
    cv2.rectangle(overlay, (x1, y1 + radius), (x2, y2 - radius), color, -1)
    cv2.circle(overlay, (x1 + radius, y1 + radius), radius, color, -1)
    cv2.circle(overlay, (x2 - radius, y1 + radius), radius, color, -1)
    cv2.circle(overlay, (x1 + radius, y2 - radius), radius, color, -1)
    cv2.circle(overlay, (x2 - radius, y2 - radius), radius, color, -1)


def get_beveled_points(top_left, bottom_right, bevel):
    x1, y1 = top_left
    x2, y2 = bottom_right
    bevel = max(6, min(bevel, (x2 - x1) // 3, (y2 - y1) // 3))
    return np.array(
        [
            (x1 + bevel, y1),
            (x2 - bevel, y1),
            (x2, y1 + bevel),
            (x2, y2 - bevel),
            (x2 - bevel, y2),
            (x1 + bevel, y2),
            (x1, y2 - bevel),
            (x1, y1 + bevel),
        ],
        dtype=np.int32,
    )


def draw_beveled_panel(overlay, top_left, bottom_right, color, bevel):
    points = get_beveled_points(top_left, bottom_right, bevel)
    cv2.fillConvexPoly(overlay, points, color, lineType=cv2.LINE_AA)


def draw_beveled_outline(overlay, top_left, bottom_right, color, bevel, thickness=1):
    points = get_beveled_points(top_left, bottom_right, bevel)
    cv2.polylines(overlay, [points], True, color, thickness=thickness, lineType=cv2.LINE_AA)


def draw_beveled_frosted_panel(frame, top_left, bottom_right, color, bevel, blur_ksize=21, alpha=0.42):
    x1, y1 = top_left
    x2, y2 = bottom_right
    x1 = max(0, x1)
    y1 = max(0, y1)
    x2 = min(frame.shape[1], x2)
    y2 = min(frame.shape[0], y2)
    if x2 <= x1 or y2 <= y1:
        return

    roi = frame[y1:y2, x1:x2].copy()
    blurred = cv2.GaussianBlur(roi, (blur_ksize, blur_ksize), 0)
    roi_h, roi_w = roi.shape[:2]
    mask = np.zeros((roi_h, roi_w), dtype=np.uint8)
    points = get_beveled_points((0, 0), (roi_w, roi_h), bevel)
    cv2.fillConvexPoly(mask, points, 255, lineType=cv2.LINE_AA)
    glass = cv2.addWeighted(blurred, 0.62, roi, 0.38, 0)
    tint = np.full_like(roi, color)
    glass = cv2.addWeighted(glass, 1.0 - alpha, tint, alpha, 0)
    roi[mask > 0] = glass[mask > 0]
    frame[y1:y2, x1:x2] = roi


def draw_focus_corner_brackets(frame, top_left, bottom_right, color_a, color_b, bevel, thickness=2):
    x1, y1 = top_left
    x2, y2 = bottom_right
    corner_len = max(18, bevel + 6)
    glow = np.zeros_like(frame)
    corners = [
        ((x1 + bevel, y1), (x1 + bevel + corner_len, y1), (x1, y1 + bevel), (x1, y1 + bevel + corner_len)),
        ((x2 - bevel, y1), (x2 - bevel - corner_len, y1), (x2, y1 + bevel), (x2, y1 + bevel + corner_len)),
        ((x1 + bevel, y2), (x1 + bevel + corner_len, y2), (x1, y2 - bevel), (x1, y2 - bevel - corner_len)),
        ((x2 - bevel, y2), (x2 - bevel - corner_len, y2), (x2, y2 - bevel), (x2, y2 - bevel - corner_len)),
    ]
    colors = [color_a, color_b, color_a, color_b]
    for color, ((hx1, hy1), (hx2, hy2), (vx1, vy1), (vx2, vy2)) in zip(colors, corners):
        cv2.line(glow, (hx1, hy1), (hx2, hy2), color, thickness + 3, lineType=cv2.LINE_AA)
        cv2.line(glow, (vx1, vy1), (vx2, vy2), color, thickness + 3, lineType=cv2.LINE_AA)
    glow = cv2.GaussianBlur(glow, (0, 0), sigmaX=5, sigmaY=5)
    cv2.addWeighted(glow, 0.32, frame, 1.0, 0, frame)
    crisp = frame.copy()
    for color, ((hx1, hy1), (hx2, hy2), (vx1, vy1), (vx2, vy2)) in zip(colors, corners):
        cv2.line(crisp, (hx1, hy1), (hx2, hy2), color, thickness, lineType=cv2.LINE_AA)
        cv2.line(crisp, (vx1, vy1), (vx2, vy2), color, thickness, lineType=cv2.LINE_AA)
    cv2.addWeighted(crisp, 0.92, frame, 0.08, 0, frame)


def draw_beveled_glow_outline(frame, top_left, bottom_right, color, bevel, alpha=0.42, thickness=2, blur_ksize=33):
    overlay = np.zeros_like(frame)
    draw_beveled_outline(overlay, top_left, bottom_right, color, bevel, thickness=max(2, thickness + 4))
    glow = cv2.GaussianBlur(overlay, (blur_ksize, blur_ksize), 0)
    cv2.addWeighted(glow, alpha, frame, 1.0, 0, frame)

    crisp = frame.copy()
    draw_beveled_outline(crisp, top_left, bottom_right, color, bevel, thickness=thickness)
    cv2.addWeighted(crisp, 0.90, frame, 0.10, 0, frame)


def draw_frosted_panel(frame, top_left, bottom_right, color, radius, blur_ksize=21, alpha=0.42):
    x1, y1 = top_left
    x2, y2 = bottom_right
    x1 = max(0, x1)
    y1 = max(0, y1)
    x2 = min(frame.shape[1], x2)
    y2 = min(frame.shape[0], y2)
    if x2 <= x1 or y2 <= y1:
        return

    roi = frame[y1:y2, x1:x2].copy()
    blurred = cv2.GaussianBlur(roi, (blur_ksize, blur_ksize), 0)
    roi_h, roi_w = roi.shape[:2]
    mask = np.zeros((roi_h, roi_w), dtype=np.uint8)
    mask_overlay = np.zeros((roi_h, roi_w, 3), dtype=np.uint8)
    draw_rounded_panel(mask_overlay, (0, 0), (roi_w, roi_h), (255, 255, 255), radius)
    mask[:, :] = mask_overlay[:, :, 0]
    glass = cv2.addWeighted(blurred, 0.65, roi, 0.35, 0)
    tint = np.full_like(roi, color)
    glass = cv2.addWeighted(glass, 1.0 - alpha, tint, alpha, 0)
    roi[mask > 0] = glass[mask > 0]
    frame[y1:y2, x1:x2] = roi


def get_status_theme(is_good):
    if is_good:
        return {
            "accent": rgb(77, 255, 149),
            "accent_soft": rgb(0, 233, 255),
            "panel": rgb(10, 16, 26),
            "bar": rgb(14, 20, 34),
            "glow": rgb(0, 233, 255),
            "glow_secondary": rgb(170, 96, 255),
            "text_primary": rgb(244, 255, 250),
            "text_secondary": rgb(220, 245, 250),
            "track": rgb(8, 8, 12),
            "fill_start": rgb(70, 214, 255),
            "fill_end": rgb(0, 255, 214),
        }
    return {
        "accent": rgb(255, 140, 64),
        "accent_soft": rgb(255, 54, 118),
        "panel": rgb(12, 14, 24),
        "bar": rgb(18, 20, 34),
        "glow": rgb(0, 215, 255),
        "glow_secondary": rgb(255, 74, 132),
        "text_primary": rgb(248, 249, 255),
        "text_secondary": rgb(238, 232, 242),
        "track": rgb(8, 8, 12),
        "fill_start": rgb(86, 198, 255),
        "fill_end": rgb(184, 114, 255),
    }


def draw_rounded_outline(overlay, top_left, bottom_right, color, radius, thickness=1):
    x1, y1 = top_left
    x2, y2 = bottom_right
    radius = max(4, min(radius, (x2 - x1) // 2, (y2 - y1) // 2))

    cv2.line(overlay, (x1 + radius, y1), (x2 - radius, y1), color, thickness, lineType=cv2.LINE_AA)
    cv2.line(overlay, (x1 + radius, y2), (x2 - radius, y2), color, thickness, lineType=cv2.LINE_AA)
    cv2.line(overlay, (x1, y1 + radius), (x1, y2 - radius), color, thickness, lineType=cv2.LINE_AA)
    cv2.line(overlay, (x2, y1 + radius), (x2, y2 - radius), color, thickness, lineType=cv2.LINE_AA)
    cv2.ellipse(overlay, (x1 + radius, y1 + radius), (radius, radius), 180, 0, 90, color, thickness, cv2.LINE_AA)
    cv2.ellipse(overlay, (x2 - radius, y1 + radius), (radius, radius), 270, 0, 90, color, thickness, cv2.LINE_AA)
    cv2.ellipse(overlay, (x1 + radius, y2 - radius), (radius, radius), 90, 0, 90, color, thickness, cv2.LINE_AA)
    cv2.ellipse(overlay, (x2 - radius, y2 - radius), (radius, radius), 0, 0, 90, color, thickness, cv2.LINE_AA)


def draw_glow_outline(frame, top_left, bottom_right, color, radius, alpha=0.42, thickness=2, blur_ksize=33):
    overlay = np.zeros_like(frame)
    draw_rounded_outline(overlay, top_left, bottom_right, color, radius, thickness=max(2, thickness + 4))
    glow = cv2.GaussianBlur(overlay, (blur_ksize, blur_ksize), 0)
    cv2.addWeighted(glow, alpha, frame, 1.0, 0, frame)

    crisp = frame.copy()
    draw_rounded_outline(crisp, top_left, bottom_right, color, radius, thickness=thickness)
    cv2.addWeighted(crisp, 0.90, frame, 0.10, 0, frame)


def draw_gradient_meter(
    frame,
    top_left,
    bottom_right,
    progress,
    start_color,
    end_color,
    bg_color,
    radius,
    marker_progress=None,
    marker_color=rgb(28, 32, 40),
):
    x1, y1 = top_left
    x2, y2 = bottom_right
    width = max(1, x2 - x1)
    height = max(1, y2 - y1)
    radius = max(4, min(radius, width // 2, height // 2))

    # Outer frosted capsule track.
    overlay = frame.copy()
    draw_rounded_panel(overlay, (x1, y1), (x2, y2), bg_color, radius)
    cv2.addWeighted(overlay, 0.72, frame, 0.28, 0, frame)

    outer_glow = frame.copy()
    draw_rounded_outline(outer_glow, (x1, y1), (x2, y2), rgb(132, 210, 255), radius, thickness=1)
    cv2.addWeighted(outer_glow, 0.34, frame, 0.66, 0, frame)

    inner_pad_x = max(8, int(round(width * 0.035)))
    inner_pad_y = max(5, int(round(height * 0.24)))
    inner_x1 = min(x2 - 2, x1 + inner_pad_x)
    inner_x2 = max(inner_x1 + 1, x2 - inner_pad_x)
    inner_y1 = min(y2 - 2, y1 + inner_pad_y)
    inner_y2 = max(inner_y1 + 1, y2 - inner_pad_y)
    inner_width = max(1, inner_x2 - inner_x1)
    inner_height = max(1, inner_y2 - inner_y1)
    inner_radius = max(3, min(inner_height // 2, radius - 2))

    inner_track = frame.copy()
    draw_rounded_panel(inner_track, (inner_x1, inner_y1), (inner_x2, inner_y2), rgb(0, 0, 0), inner_radius)
    cv2.addWeighted(inner_track, 0.78, frame, 0.22, 0, frame)

    fill_width = max(1, int(round(inner_width * float(np.clip(progress, 0.0, 1.0)))))
    fill_overlay = np.zeros_like(frame)
    bar_roi = np.zeros((inner_height, fill_width, 3), dtype=np.uint8)
    start = np.array(start_color, dtype=np.float32)
    end = np.array(end_color, dtype=np.float32)
    blend = np.linspace(0.0, 1.0, fill_width, dtype=np.float32)[:, None]
    gradient = start[None, :] * (1.0 - blend) + end[None, :] * blend
    bar_roi[:, :, :] = gradient[None, :, :].astype(np.uint8)
    fill_overlay[inner_y1:inner_y2, inner_x1:inner_x1 + fill_width] = bar_roi

    mask = np.zeros((inner_height, fill_width, 3), dtype=np.uint8)
    draw_rounded_panel(mask, (0, 0), (fill_width, inner_height), (255, 255, 255), inner_radius)
    roi = fill_overlay[inner_y1:inner_y2, inner_x1:inner_x1 + fill_width]
    fill_overlay[inner_y1:inner_y2, inner_x1:inner_x1 + fill_width] = cv2.bitwise_and(roi, mask)

    gradient_glow = cv2.GaussianBlur(fill_overlay, (0, 0), sigmaX=max(3, inner_height), sigmaY=max(2, inner_height // 2))
    cv2.addWeighted(gradient_glow, 0.26, frame, 1.0, 0, frame)
    cv2.addWeighted(fill_overlay, 0.88, frame, 1.0, 0, frame)

    # Add a cyberpunk-style moving sheen so the bars feel less visually weak.
    if fill_width > 8:
        sheen_center = int(round(fill_width * (0.25 + 0.55 * float(np.clip(progress, 0.0, 1.0)))))
        sheen_half = max(8, fill_width // 10)
        sheen_x1 = max(0, sheen_center - sheen_half)
        sheen_x2 = min(fill_width, sheen_center + sheen_half)
        if sheen_x2 > sheen_x1:
            sheen_overlay = np.zeros_like(frame)
            sheen_w = sheen_x2 - sheen_x1
            sheen_roi = np.zeros((inner_height, sheen_w, 3), dtype=np.uint8)
            sheen_strength = np.linspace(0.0, 1.0, sheen_w, dtype=np.float32)
            sheen_strength = np.minimum(sheen_strength, sheen_strength[::-1]) * 2.0
            sheen_color = np.array(rgb(235, 248, 255), dtype=np.float32)
            sheen_roi[:, :, :] = (sheen_color[None, None, :] * sheen_strength[None, :, None]).astype(np.uint8)
            sheen_overlay[inner_y1:inner_y2, inner_x1 + sheen_x1:inner_x1 + sheen_x2] = sheen_roi
            sheen_mask = np.zeros((inner_height, sheen_w, 3), dtype=np.uint8)
            draw_rounded_panel(sheen_mask, (0, 0), (sheen_w, inner_height), (255, 255, 255), inner_radius)
            sheen_overlay[inner_y1:inner_y2, inner_x1 + sheen_x1:inner_x1 + sheen_x2] = cv2.bitwise_and(
                sheen_overlay[inner_y1:inner_y2, inner_x1 + sheen_x1:inner_x1 + sheen_x2],
                sheen_mask,
            )
            sheen_overlay = cv2.GaussianBlur(sheen_overlay, (0, 0), sigmaX=max(2, inner_height // 2), sigmaY=max(1, inner_height // 3))
            cv2.addWeighted(sheen_overlay, 0.26, frame, 1.0, 0, frame)

    outline = frame.copy()
    draw_rounded_outline(outline, (x1, y1), (x2, y2), rgb(104, 154, 198), radius, thickness=1)
    cv2.addWeighted(outline, 0.44, frame, 0.56, 0, frame)

    if marker_progress is not None:
        marker_x = int(round(inner_x1 + inner_width * float(np.clip(marker_progress, 0.0, 1.0))))
        marker_x = int(np.clip(marker_x, inner_x1 + inner_radius, inner_x2 - inner_radius))
        marker_overlay = frame.copy()
        cv2.line(
            marker_overlay,
            (marker_x, y1 - max(3, height // 5)),
            (marker_x, y2 + max(2, height // 7)),
            marker_color,
            max(2, height // 8),
            lineType=cv2.LINE_AA,
        )
        cv2.addWeighted(marker_overlay, 0.92, frame, 0.08, 0, frame)
        marker_head = np.array(
            [
                (marker_x, y1 - max(6, height // 3)),
                (marker_x - max(4, height // 5), y1 + max(1, height // 8)),
                (marker_x + max(4, height // 5), y1 + max(1, height // 8)),
            ],
            dtype=np.int32,
        )
        cv2.fillConvexPoly(frame, marker_head, marker_color, lineType=cv2.LINE_AA)


def draw_info_icon(frame, center, radius, color, font):
    overlay = frame.copy()
    cv2.circle(overlay, center, radius, color, -1, lineType=cv2.LINE_AA)
    cv2.addWeighted(overlay, 0.28, frame, 0.72, 0, frame)
    cv2.circle(frame, center, radius, (236, 243, 250), 1, lineType=cv2.LINE_AA)
    text = "i"
    width = measure_text_width(text, font, fallback_scale=0.72, fallback_thickness=2)
    draw_text(
        frame,
        text,
        (center[0] - width // 2, center[1] - radius + 1),
        (248, 251, 255),
        font,
        fallback_scale=0.72,
        fallback_thickness=2,
    )


def draw_benchpress_icon(frame, top_left, size, color_primary, color_secondary):
    x, y = top_left
    w = max(36, int(size))
    h = max(28, int(round(w * 0.76)))
    overlay = frame.copy()

    head_center = (x + int(round(w * 0.78)), y + int(round(h * 0.18)))
    cv2.circle(overlay, head_center, max(3, w // 12), color_secondary, -1, lineType=cv2.LINE_AA)

    body_start = (x + int(round(w * 0.70)), y + int(round(h * 0.28)))
    body_mid = (x + int(round(w * 0.56)), y + int(round(h * 0.48)))
    hip = (x + int(round(w * 0.42)), y + int(round(h * 0.58)))
    knee = (x + int(round(w * 0.58)), y + int(round(h * 0.82)))
    foot = (x + int(round(w * 0.76)), y + int(round(h * 0.82)))
    bench_back = (x + int(round(w * 0.28)), y + int(round(h * 0.44)))
    bench_front = (x + int(round(w * 0.10)), y + int(round(h * 0.64)))

    cv2.line(overlay, body_start, body_mid, color_primary, 3, lineType=cv2.LINE_AA)
    cv2.line(overlay, body_mid, hip, color_primary, 3, lineType=cv2.LINE_AA)
    cv2.line(overlay, hip, knee, color_primary, 3, lineType=cv2.LINE_AA)
    cv2.line(overlay, knee, foot, color_primary, 3, lineType=cv2.LINE_AA)
    cv2.line(overlay, body_mid, bench_back, color_secondary, 3, lineType=cv2.LINE_AA)
    cv2.line(overlay, bench_back, bench_front, color_secondary, 3, lineType=cv2.LINE_AA)
    cv2.line(overlay, bench_front, (bench_front[0], y + h), color_secondary, 2, lineType=cv2.LINE_AA)
    cv2.line(overlay, bench_back, (bench_back[0], y + h), color_secondary, 2, lineType=cv2.LINE_AA)

    bar_y = y + int(round(h * 0.22))
    bar_x1 = x + int(round(w * 0.08))
    bar_x2 = x + int(round(w * 0.94))
    cv2.line(overlay, (bar_x1, bar_y), (bar_x2, bar_y), color_primary, 3, lineType=cv2.LINE_AA)
    for plate_x in (bar_x1 + w // 16, bar_x2 - w // 16):
        cv2.circle(overlay, (plate_x, bar_y), max(3, w // 11), color_secondary, 2, lineType=cv2.LINE_AA)

    arm_left = (x + int(round(w * 0.54)), y + int(round(h * 0.30)))
    arm_right = (x + int(round(w * 0.66)), y + int(round(h * 0.28)))
    cv2.line(overlay, body_start, arm_left, color_primary, 3, lineType=cv2.LINE_AA)
    cv2.line(overlay, body_start, arm_right, color_primary, 3, lineType=cv2.LINE_AA)
    cv2.line(overlay, arm_left, (x + int(round(w * 0.36)), bar_y), color_primary, 3, lineType=cv2.LINE_AA)
    cv2.line(overlay, arm_right, (x + int(round(w * 0.70)), bar_y), color_primary, 3, lineType=cv2.LINE_AA)

    cv2.addWeighted(overlay, 0.88, frame, 0.12, 0, frame)


def draw_report_glow(frame, center, radius, color):
    overlay = np.zeros_like(frame)
    cv2.circle(overlay, center, radius, color, -1, lineType=cv2.LINE_AA)
    glow = cv2.GaussianBlur(overlay, (0, 0), sigmaX=radius * 0.32, sigmaY=radius * 0.32)
    cv2.addWeighted(glow, 0.34, frame, 1.0, 0, frame)


def draw_status_pill(frame, text, position, text_color, fill_color, font, ui_scale, width_override=None):
    x, y = position
    width = width_override if width_override is not None else max(340, int(round(500 * ui_scale)))
    height = max(48, int(round(64 * ui_scale)))
    radius = max(10, int(round(height * 0.45)))

    overlay = frame.copy()
    draw_rounded_panel(overlay, (x, y), (x + width, y + height), fill_color, radius)
    cv2.addWeighted(overlay, 0.96, frame, 0.04, 0, frame)
    cv2.rectangle(frame, (x, y), (x + width, y + height), (252, 252, 252), 1)
    draw_text(
        frame,
        text,
        (x + max(12, int(round(18 * ui_scale))), y + max(6, int(round(9 * ui_scale)))),
        text_color,
        font,
        fallback_scale=max(1.08, 1.16 * ui_scale),
        fallback_thickness=4,
    )


def clamp_text(text, max_chars):
    if len(text) <= max_chars:
        return text
    return text[: max_chars - 1] + "…"


def draw_bottom_hint(frame, hint_text, hint_color, hint_font, ui_scale):
    width_scale = frame.shape[1] / 1080.0
    hint_scale = max(ui_scale, width_scale)
    bar_height = max(56, int(round(64 * hint_scale)))
    margin = max(22, int(round(28 * hint_scale)))
    left = max(margin, int(frame.shape[1] * 0.14))
    right = min(frame.shape[1] - margin, int(frame.shape[1] * 0.86))
    y1 = max(0, frame.shape[0] - bar_height - margin)
    y2 = frame.shape[0] - margin
    radius = max(14, int(round(18 * hint_scale)))
    is_good = hint_color == (80, 255, 120)
    bg_color = rgb(236, 242, 248)
    border_color = rgb(182, 198, 214)
    text_color = rgb(92, 68, 50)
    icon_color = rgb(125, 166, 202)
    shadow_overlay = frame.copy()
    draw_rounded_panel(shadow_overlay, (left + 4, y1 + 6), (right + 4, y2 + 6), rgb(0, 0, 0), radius)
    cv2.addWeighted(shadow_overlay, 0.12, frame, 0.88, 0, frame)
    bottom_overlay = frame.copy()
    draw_rounded_panel(bottom_overlay, (left, y1), (right, y2), bg_color, radius)
    cv2.addWeighted(bottom_overlay, 0.82, frame, 0.18, 0, frame)
    draw_rounded_outline(frame, (left, y1), (right, y2), border_color, radius, thickness=2)

    icon_radius = max(8, int(round(10 * hint_scale)))
    icon_center = (left + max(24, int(round(30 * hint_scale))), (y1 + y2) // 2)
    draw_info_icon(frame, icon_center, icon_radius, icon_color, hint_font)

    info_text = hint_text
    text_left = icon_center[0] + icon_radius + max(12, int(round(14 * hint_scale)))
    text_right = right - max(14, int(round(18 * hint_scale)))
    text_available_width = max(1, text_right - text_left)
    hint_text_font = fit_chinese_font_to_width(
        info_text,
        text_available_width,
        preferred_size=scaled_font_size(28, max(1.02, hint_scale)),
        min_size=scaled_font_size(18, max(1.00, hint_scale)),
        max_size=scaled_font_size(36, max(1.10, hint_scale)),
    )
    hint_fallback_scale = max(1.02, 1.12 * hint_scale)
    if hint_text_font is None:
        for scale in np.linspace(max(1.5, 1.7 * hint_scale), max(1.02, 1.12 * hint_scale), num=10):
            if measure_text_width(info_text, None, fallback_scale=float(scale), fallback_thickness=5) <= text_available_width:
                hint_fallback_scale = float(scale)
                break
    hint_width = measure_text_width(
        info_text,
        hint_text_font,
        fallback_scale=hint_fallback_scale,
        fallback_thickness=5,
    )
    text_x = max(text_left, (left + right - hint_width) // 2)
    text_y = y1 + max(6, int(round(8 * hint_scale)))
    draw_text_crisp(
        frame,
        info_text,
        (text_x, text_y),
        text_color,
        hint_text_font,
        fallback_scale=hint_fallback_scale,
        fallback_thickness=5,
        stroke_width=1,
        stroke_fill=(244, 241, 236),
    )


def draw_score_panel(frame, class_names, display_names, scores, top_k, score_threshold, frames_ready, sequence_length, zh_font, small_zh_font, ui_scale):
    panel_scale = max(1.0, ui_scale)
    radius = max(12, int(round(14 * panel_scale)))
    pad_x = max(18, int(round(20 * panel_scale)))
    pad_y = max(14, int(round(16 * panel_scale)))
    card_gap = max(16, int(round(18 * panel_scale)))
    base_card_h = max(110, int(round(122 * panel_scale)))
    meter_height = max(26, int(round(30 * panel_scale)))
    meter_gap = max(14, int(round(16 * panel_scale)))
    left_margin = max(18, int(round(frame.shape[1] * 0.03)))
    top_margin = max(18, int(round(frame.shape[0] * 0.04)))
    left_card_width = max(130, int(round(frame.shape[1] * 0.11)))
    right_card_width = max(300, int(round(frame.shape[1] * 0.26)))
    right_panel_pad_x = max(18, int(round(20 * panel_scale)))
    right_panel_pad_y = max(16, int(round(18 * panel_scale)))

    score_label = "当前评分"
    action_label = "当前动作"
    status_label = "当前状态"
    card_inner_top = max(14, int(round(16 * panel_scale)))
    card_inner_bottom = max(16, int(round(18 * panel_scale)))
    label_to_content_gap = max(14, int(round(16 * panel_scale)))

    if scores is None:
        score_card_h = action_card_h = status_card_h = base_card_h
    else:
        ranked_indices = np.argsort(scores)[::-1]
        best_index = int(ranked_indices[0])
        best_score = float(scores[best_index])
        hero_text = clamp_text(display_names[best_index], 8)
        best_score_text = f"{best_score * 100:.1f}"
        status_text = "很标准" if best_score >= LOW_SCORE_HINT_THRESHOLD else "需调整"

        label_font = fit_chinese_font_to_width(
            score_label,
            int(left_card_width * 0.72),
            preferred_size=scaled_font_size(26, panel_scale),
            min_size=scaled_font_size(18, panel_scale),
            max_size=scaled_font_size(32, panel_scale),
        )
        score_font = fit_tech_font_to_width(
            f"{best_score_text}%",
            int(left_card_width * 0.80),
            preferred_size=scaled_font_size(46, max(1.0, panel_scale)),
            min_size=scaled_font_size(30, max(1.0, panel_scale)),
            max_size=scaled_font_size(58, max(1.08, panel_scale)),
        )
        action_font = fit_chinese_font_to_width(
            hero_text,
            int(left_card_width * 0.82),
            preferred_size=scaled_font_size(40, panel_scale),
            min_size=scaled_font_size(26, panel_scale),
            max_size=scaled_font_size(50, panel_scale),
        )
        status_font = fit_chinese_font_to_width(
            status_text,
            int(right_card_width * 0.78),
            preferred_size=scaled_font_size(40, panel_scale),
            min_size=scaled_font_size(26, panel_scale),
            max_size=scaled_font_size(50, panel_scale),
        )

        score_label_h = measure_text_height(score_label, label_font, fallback_scale=max(1.18, 1.30 * panel_scale), fallback_thickness=5)
        action_label_h = measure_text_height(action_label, label_font, fallback_scale=max(1.18, 1.30 * panel_scale), fallback_thickness=5)
        status_label_h = measure_text_height(status_label, label_font, fallback_scale=max(1.18, 1.30 * panel_scale), fallback_thickness=5)
        score_content_h = measure_text_height(f"{best_score_text}%", score_font, fallback_scale=max(2.00, 2.20 * panel_scale), fallback_thickness=6)
        action_content_h = measure_text_height(hero_text, action_font, fallback_scale=max(1.34, 1.48 * panel_scale), fallback_thickness=5)
        status_content_h = measure_text_height(status_text, status_font, fallback_scale=max(1.34, 1.48 * panel_scale), fallback_thickness=5)

        score_card_h = max(base_card_h, card_inner_top + score_label_h + label_to_content_gap + score_content_h + card_inner_bottom)
        action_card_h = max(base_card_h, card_inner_top + action_label_h + label_to_content_gap + action_content_h + card_inner_bottom)
        status_card_h = max(base_card_h, card_inner_top + status_label_h + label_to_content_gap + status_content_h + card_inner_bottom)

    score_card_x1 = left_margin
    score_card_x2 = score_card_x1 + left_card_width
    score_card_y1 = top_margin
    score_card_y2 = score_card_y1 + score_card_h
    action_card_x1 = score_card_x1
    action_card_x2 = score_card_x2
    action_card_y1 = score_card_y2 + card_gap
    action_card_y2 = action_card_y1 + action_card_h
    right_panel_x2 = frame.shape[1] - max(18, int(round(frame.shape[1] * 0.03)))
    right_panel_x1 = right_panel_x2 - (right_card_width + right_panel_pad_x * 2)
    right_panel_y1 = top_margin
    right_panel_y2 = right_panel_y1 + status_card_h + meter_gap + meter_height + right_panel_pad_y * 2
    status_card_x1 = right_panel_x1 + right_panel_pad_x
    status_card_x2 = right_panel_x2 - right_panel_pad_x
    status_card_y1 = right_panel_y1 + right_panel_pad_y
    status_card_y2 = status_card_y1 + status_card_h
    meter_y = status_card_y2 + meter_gap

    is_good = scores is not None and float(np.max(scores)) >= LOW_SCORE_HINT_THRESHOLD

    def draw_card(x1, y1, x2, y2, fill_color, outline_color):
        shadow_overlay = frame.copy()
        draw_rounded_panel(shadow_overlay, (x1 + 4, y1 + 6), (x2 + 4, y2 + 6), rgb(0, 0, 0), max(8, radius - 2))
        cv2.addWeighted(shadow_overlay, 0.12, frame, 0.88, 0, frame)
        card_overlay = frame.copy()
        draw_rounded_panel(card_overlay, (x1, y1), (x2, y2), fill_color, max(8, radius - 2))
        cv2.addWeighted(card_overlay, 0.84, frame, 0.16, 0, frame)
        draw_rounded_outline(frame, (x1, y1), (x2, y2), outline_color, max(8, radius - 2), thickness=2)

    if scores is None:
        loading_card_width = max(280, int(round(frame.shape[1] * 0.26)))
        loading_card_height = max(base_card_h, int(round(122 * panel_scale)))
        loading_card_x1 = (frame.shape[1] - loading_card_width) // 2
        loading_card_x2 = loading_card_x1 + loading_card_width
        loading_card_y1 = (frame.shape[0] - loading_card_height) // 2
        loading_card_y2 = loading_card_y1 + loading_card_height
        draw_card(loading_card_x1, loading_card_y1, loading_card_x2, loading_card_y2, rgb(236, 242, 248), rgb(182, 198, 214))
        loading_text = "识别中..."
        loading_font = fit_chinese_font_to_width(
            loading_text,
            int((loading_card_x2 - loading_card_x1 - pad_x * 2) * 0.9),
            preferred_size=scaled_font_size(34, panel_scale),
            min_size=scaled_font_size(18, panel_scale),
            max_size=scaled_font_size(40, panel_scale),
        )
        loading_scale = max(1.18, 1.30 * panel_scale)
        loading_width = measure_text_width(loading_text, loading_font, fallback_scale=loading_scale, fallback_thickness=6)
        loading_text_h = measure_text_height(loading_text, loading_font, fallback_scale=loading_scale, fallback_thickness=6)
        loading_y = loading_card_y1 + max(0, (loading_card_height - loading_text_h) // 2)
        draw_text_crisp(
            frame,
            loading_text,
            ((loading_card_x1 + loading_card_x2 - loading_width) // 2, loading_y),
            rgb(35, 81, 132),
            loading_font,
            fallback_scale=loading_scale,
            fallback_thickness=6,
            stroke_width=1,
            stroke_fill=(255, 255, 255),
        )
        return

    ranked_indices = np.argsort(scores)[::-1]
    best_index = int(ranked_indices[0])
    best_score = float(scores[best_index])
    hero_text = clamp_text(display_names[best_index], 8)
    best_score_text = f"{best_score * 100:.1f}"
    status_text = "很标准" if best_score >= LOW_SCORE_HINT_THRESHOLD else "需调整"
    text_primary = rgb(92, 68, 50)
    text_dark = rgb(92, 68, 50)
    text_muted = rgb(88, 62, 44)
    score_color = rgb(104, 72, 52)
    card_bg = rgb(242, 247, 252)
    status_card_bg = rgb(228, 242, 236) if is_good else rgb(243, 235, 229)
    status_color = rgb(60, 156, 88) if is_good else rgb(216, 124, 54)
    draw_card(score_card_x1, score_card_y1, score_card_x2, score_card_y2, card_bg, rgb(204, 216, 228))
    draw_card(action_card_x1, action_card_y1, action_card_x2, action_card_y2, card_bg, rgb(204, 216, 228))
    draw_card(right_panel_x1, right_panel_y1, right_panel_x2, right_panel_y2, rgb(236, 242, 248), rgb(182, 198, 214))
    draw_card(status_card_x1, status_card_y1, status_card_x2, status_card_y2, status_card_bg, rgb(205, 222, 205))

    label_font = fit_chinese_font_to_width(
        score_label,
        int(left_card_width * 0.72),
        preferred_size=scaled_font_size(30, panel_scale),
        min_size=scaled_font_size(18, panel_scale),
        max_size=scaled_font_size(36, panel_scale),
    )
    score_label_x = score_card_x1 + pad_x
    action_label_x = action_card_x1 + pad_x
    status_label_x = status_card_x1 + pad_x
    score_label_h = measure_text_height(score_label, label_font, fallback_scale=max(1.18, 1.30 * panel_scale), fallback_thickness=5)
    action_label_h = measure_text_height(action_label, label_font, fallback_scale=max(1.18, 1.30 * panel_scale), fallback_thickness=5)
    status_label_h = measure_text_height(status_label, label_font, fallback_scale=max(1.18, 1.30 * panel_scale), fallback_thickness=5)
    score_label_y = score_card_y1 + card_inner_top
    action_label_y = action_card_y1 + card_inner_top
    status_label_y = status_card_y1 + card_inner_top
    draw_text_crisp(
        frame,
        score_label,
        (score_label_x, score_label_y),
        text_muted,
        label_font,
        fallback_scale=max(1.30, 1.42 * panel_scale),
        fallback_thickness=5,
        stroke_width=1,
        stroke_fill=(244, 241, 236),
    )
    score_font = fit_tech_font_to_width(
        f"{best_score_text}%",
        int(left_card_width * 0.80),
        preferred_size=scaled_font_size(46, max(1.0, panel_scale)),
        min_size=scaled_font_size(30, max(1.0, panel_scale)),
        max_size=scaled_font_size(58, max(1.08, panel_scale)),
    )
    score_scale = max(2.00, 2.20 * panel_scale)
    score_full_text = f"{best_score_text}%"
    score_text_y = score_label_y + score_label_h + label_to_content_gap
    draw_text_crisp(
        frame,
        score_full_text,
        (score_label_x, score_text_y),
        score_color,
        score_font,
        fallback_scale=score_scale,
        fallback_thickness=6,
        stroke_width=1,
        stroke_fill=(255, 255, 255),
    )

    draw_text_crisp(
        frame,
        action_label,
        (action_label_x, action_label_y),
        text_muted,
        label_font,
        fallback_scale=max(1.30, 1.42 * panel_scale),
        fallback_thickness=5,
        stroke_width=1,
        stroke_fill=(244, 241, 236),
    )
    action_font = fit_chinese_font_to_width(
        hero_text,
        int(left_card_width * 0.82),
        preferred_size=scaled_font_size(40, panel_scale),
        min_size=scaled_font_size(26, panel_scale),
        max_size=scaled_font_size(50, panel_scale),
    )
    action_text_y = action_label_y + action_label_h + label_to_content_gap
    draw_text_crisp(
        frame,
        hero_text,
        (action_label_x, action_text_y),
        text_dark,
        action_font,
        fallback_scale=max(1.34, 1.48 * panel_scale),
        fallback_thickness=5,
        stroke_width=1,
        stroke_fill=(255, 255, 255),
    )

    draw_text_crisp(
        frame,
        status_label,
        (status_label_x, status_label_y),
        text_muted,
        label_font,
        fallback_scale=max(1.30, 1.42 * panel_scale),
        fallback_thickness=5,
        stroke_width=1,
        stroke_fill=(244, 241, 236),
    )
    status_font = fit_chinese_font_to_width(
        status_text,
        int(right_card_width * 0.78),
        preferred_size=scaled_font_size(40, panel_scale),
        min_size=scaled_font_size(26, panel_scale),
        max_size=scaled_font_size(50, panel_scale),
    )
    status_text_y = status_label_y + status_label_h + label_to_content_gap
    draw_text_crisp(
        frame,
        status_text,
        (status_label_x, status_text_y),
        status_color,
        status_font,
        fallback_scale=max(1.34, 1.48 * panel_scale),
        fallback_thickness=5,
        stroke_width=1,
        stroke_fill=(255, 255, 255),
    )

    meter_x1 = status_card_x1
    meter_x2 = status_card_x2
    draw_gradient_meter(
        frame,
        (meter_x1, meter_y),
        (meter_x2, meter_y + meter_height),
        best_score,
        rgb(78, 174, 255),
        rgb(142, 208, 255),
        rgb(228, 228, 228),
        radius=max(6, meter_height // 2),
        marker_progress=LOW_SCORE_HINT_THRESHOLD,
        marker_color=rgb(96, 96, 96),
    )


def main():
    args = parse_args()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    class_names = load_class_names(args.class_mapping)
    display_names = build_display_names(class_names)
    model, config = load_model(args.model_path, device)
    use_velocity = config.get("use_velocity", True)
    sequence_length = args.sequence_length
    top_k = max(1, min(args.top_k, len(class_names)))
    smooth_factor = float(np.clip(args.smooth_factor, 0.0, 0.99))
    ui_scale = float(np.clip(args.ui_scale, 0.7, 1.6))
    zh_font = load_chinese_font(scaled_font_size(38, max(1.05, ui_scale)))
    small_zh_font = load_chinese_font(scaled_font_size(30, max(1.05, ui_scale)))
    screen_width, screen_height = get_screen_size()

    source = resolve_video_source(args.source)
    capture = cv2.VideoCapture(source)
    if not capture.isOpened():
        raise RuntimeError(f"无法打开视频源: {args.source}")
    is_camera_source = isinstance(source, int)
    source_fps = capture.get(cv2.CAP_PROP_FPS)
    source_width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    source_height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    if not source_fps or source_fps <= 1 or np.isnan(source_fps):
        source_fps = 30.0
    target_frame_ms = 1000.0 / source_fps
    preview_width, preview_height = get_preview_window_size(
        source_width or 1280,
        source_height or 720,
        screen_width,
        screen_height,
    )

    mp_pose = mp.solutions.pose
    mp_drawing = mp.solutions.drawing_utils
    mp_styles = mp.solutions.drawing_styles

    sequence_buffer = deque(maxlen=sequence_length)
    smoothed_scores = None

    print("=" * 72)
    print("视觉识别动作分数展示")
    print("=" * 72)
    print(f"视频源: {args.source}")
    print(f"模型路径: {args.model_path}")
    print(f"使用设备: {device}")
    print(f"中文显示: {'已启用' if small_zh_font is not None else '未启用，缺少 Pillow 或中文字体'}")
    print("显示模式: 保持原始视频帧，仅适配预览窗口")
    print("按键说明: 按 q 退出")
    print("=" * 72)

    cv2.namedWindow("Fitness Action Recognition", cv2.WINDOW_NORMAL)
    cv2.resizeWindow("Fitness Action Recognition", preview_width, preview_height)

    with mp_pose.Pose(
        static_image_mode=False,
        model_complexity=1,
        enable_segmentation=False,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    ) as pose:
        while True:
            loop_start = time.perf_counter()
            success, frame = capture.read()
            if not success:
                print("视频读取结束或摄像头无法继续读取。")
                break

            pose_frame, pose_result = extract_pose_sequence(frame, pose)
            if pose_frame is not None:
                sequence_buffer.append(pose_frame)

            ready_frames = len(sequence_buffer)
            display_scores = None
            if ready_frames >= max(1, args.min_frames):
                model_input = prepare_model_input(sequence_buffer, sequence_length, use_velocity)
                current_scores = predict_scores(model, model_input, device)
                current_scores = apply_benchpress_highjump_heuristic(current_scores, class_names, sequence_buffer)
                current_scores = apply_benchpress_stillrings_heuristic(current_scores, class_names, sequence_buffer)
                current_scores = apply_ropeclimbing_lunges_heuristic(current_scores, class_names, sequence_buffer)
                current_scores = apply_upper_body_climb_heuristic(current_scores, class_names, sequence_buffer)

                if smoothed_scores is None:
                    smoothed_scores = current_scores
                else:
                    smoothed_scores = smooth_factor * smoothed_scores + (1.0 - smooth_factor) * current_scores
                display_scores = smoothed_scores

            draw_pose(frame, pose_result, mp_drawing, mp_styles, mp_pose)
            draw_score_panel(
                frame,
                class_names,
                display_names,
                display_scores,
                top_k,
                args.score_threshold,
                ready_frames,
                sequence_length,
                zh_font,
                small_zh_font,
                ui_scale,
            )

            cv2.imshow("Fitness Action Recognition", frame)
            elapsed_ms = (time.perf_counter() - loop_start) * 1000.0
            wait_ms = 1 if is_camera_source else max(1, int(round(target_frame_ms - elapsed_ms)))
            if cv2.waitKey(wait_ms) & 0xFF == ord("q"):
                break

            if not is_camera_source and elapsed_ms > target_frame_ms:
                frames_to_skip = min(5, max(0, int(elapsed_ms // target_frame_ms) - 1))
                for _ in range(frames_to_skip):
                    if not capture.grab():
                        break

    capture.release()
    cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
