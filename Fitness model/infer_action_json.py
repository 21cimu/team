import argparse
import json
import os
import sys
from collections import deque

import cv2
import mediapipe as mp
import numpy as np
import torch

from visualize_action_scores import (
    ACTION_NAME_ZH,
    LOW_SCORE_HINT_THRESHOLD,
    apply_benchpress_highjump_heuristic,
    apply_benchpress_stillrings_heuristic,
    apply_ropeclimbing_lunges_heuristic,
    apply_upper_body_climb_heuristic,
    build_display_names,
    extract_pose_sequence,
    load_class_names,
    load_model,
    predict_scores,
    prepare_model_input,
)


ACTION_ADVICE = {
    "BenchPress": [
        "Keep both wrists stacked over the elbows.",
        "Stabilize the shoulder blades before pressing.",
        "Use a flatter camera angle so the full torso stays visible.",
    ],
    "BodyWeightSquats": [
        "Push the hips back first and keep the knees tracking with the toes.",
        "Keep the chest lifted through the bottom position.",
        "Record from a side-front angle so depth is easier to read.",
    ],
    "JumpingJack": [
        "Keep the landing rhythm even and avoid collapsing inward at the knees.",
        "Open the arms fully overhead to improve action completeness.",
        "Leave more space above the head in the frame.",
    ],
    "JumpRope": [
        "Keep the elbows close to the body and reduce unnecessary shoulder swing.",
        "Land softly on the forefoot and keep cadence steady.",
        "Make sure the full lower body is inside the frame.",
    ],
    "Lunges": [
        "Maintain a long split stance and keep the torso upright.",
        "Lower straight down instead of drifting forward.",
        "Use a side angle to show hip and knee alignment more clearly.",
    ],
    "PullUps": [
        "Start from a stable hang and avoid excessive leg swing.",
        "Drive the elbows down instead of pulling the chin forward.",
        "Keep the bar and full upper body visible in the frame.",
    ],
    "PushUps": [
        "Brace the core and avoid hip sagging.",
        "Lower until the elbows reach a clear bend before pressing back up.",
        "Use a slightly diagonal side view for better posture detection.",
    ],
    "WallPushups": [
        "Keep the body in one line from shoulders to ankles.",
        "Control the lowering phase instead of bouncing off the wall.",
        "Frame the full body and the wall contact point together.",
    ],
}


def parse_args():
    parser = argparse.ArgumentParser(description="Infer fitness action from a local video file.")
    parser.add_argument("--source", required=True, help="Local video path")
    parser.add_argument("--model-path", default="./trained_models/best_model.pth")
    parser.add_argument("--class-mapping", default="./processed_data/class_mapping.json")
    parser.add_argument("--sequence-length", type=int, default=100)
    parser.add_argument("--top-k", type=int, default=3)
    parser.add_argument("--min-frames", type=int, default=20)
    parser.add_argument("--smooth-factor", type=float, default=0.7)
    return parser.parse_args()


def build_hint(score, pose_frames, total_frames):
    coverage = 0.0 if total_frames <= 0 else pose_frames / total_frames
    if score >= 0.75:
        return "Action is recognized steadily. You can keep the current pace and continue refining range and control."
    if score >= LOW_SCORE_HINT_THRESHOLD:
        return "Recognition is available but not stable enough. Slow the tempo slightly and keep the full body in frame."
    if coverage < 0.35:
        return "Too few valid body landmarks were extracted. Improve lighting, distance, and camera angle first."
    return "Confidence is low. The movement may be incomplete or the camera angle does not show the key joints clearly."


def build_advice(label, score):
    generic = [
        "Keep the whole body inside the frame from start to finish.",
        "Use brighter lighting and avoid heavy backlight or blur.",
        "Record 3 to 6 seconds of continuous movement at a stable angle.",
    ]
    action_specific = ACTION_ADVICE.get(label, [])
    if score >= 0.75:
        return action_specific[:2] + generic[:1]
    if score >= LOW_SCORE_HINT_THRESHOLD:
        return action_specific[:2] + generic[:2]
    return action_specific[:2] + generic


def serialize_predictions(class_names, display_names, scores, top_k):
    ranked = np.argsort(scores)[::-1][:top_k]
    predictions = []
    for idx in ranked:
        predictions.append(
            {
                "label": class_names[idx],
                "labelZh": display_names[idx],
                "score": round(float(scores[idx]), 6),
                "scorePercent": int(round(float(scores[idx]) * 100)),
            }
        )
    return predictions


def run_inference(args):
    if not os.path.exists(args.source):
        raise FileNotFoundError(f"Video file does not exist: {args.source}")

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    class_names = load_class_names(args.class_mapping)
    display_names = build_display_names(class_names)
    model, config = load_model(args.model_path, device)
    use_velocity = config.get("use_velocity", True)
    smooth_factor = float(np.clip(args.smooth_factor, 0.0, 0.99))
    sequence_length = max(1, int(args.sequence_length))
    top_k = max(1, min(int(args.top_k), len(class_names)))

    capture = cv2.VideoCapture(args.source)
    if not capture.isOpened():
        raise RuntimeError(f"Cannot open video: {args.source}")

    mp_pose = mp.solutions.pose
    sequence_buffer = deque(maxlen=sequence_length)
    smoothed_scores = None
    score_history = []
    total_frames = 0
    pose_frames = 0

    with mp_pose.Pose(
        static_image_mode=False,
        model_complexity=1,
        enable_segmentation=False,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5,
    ) as pose:
        while True:
            success, frame = capture.read()
            if not success:
                break

            total_frames += 1
            pose_frame, _ = extract_pose_sequence(frame, pose)
            if pose_frame is not None:
                pose_frames += 1
                sequence_buffer.append(pose_frame)

            if len(sequence_buffer) < max(1, args.min_frames):
                continue

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

            score_history.append(smoothed_scores.copy())

    capture.release()

    if not score_history:
        raise RuntimeError("No stable action prediction was produced. Please upload a clearer fitness video.")

    history_window = min(12, len(score_history))
    final_scores = np.mean(score_history[-history_window:], axis=0)
    final_scores = final_scores / np.clip(np.sum(final_scores), 1e-6, None)
    best_idx = int(np.argmax(final_scores))
    best_label = class_names[best_idx]
    best_score = float(final_scores[best_idx])

    return {
        "success": True,
        "label": best_label,
        "labelZh": ACTION_NAME_ZH.get(best_label, display_names[best_idx]),
        "score": round(best_score, 6),
        "scorePercent": int(round(best_score * 100)),
        "standard": best_score >= LOW_SCORE_HINT_THRESHOLD,
        "hint": build_hint(best_score, pose_frames, total_frames),
        "advice": build_advice(best_label, best_score),
        "topK": serialize_predictions(class_names, display_names, final_scores, top_k),
        "poseFrames": pose_frames,
        "totalFrames": total_frames,
        "sequenceFrames": sequence_length,
        "source": "mediapipe-pytorch",
    }


def main():
    os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")
    args = parse_args()
    result = run_inference(args)
    sys.stdout.write(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
