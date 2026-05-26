import argparse
import base64
import json
import os
import sys
from collections import deque

import cv2
import numpy as np

import infer_action_json as batch


def parse_args():
    parser = argparse.ArgumentParser(description="Realtime fitness action inference server over stdin/stdout.")
    parser.add_argument("--model-path", default="./trained_models/best_model.pth")
    parser.add_argument("--class-mapping", default="./processed_data/class_mapping.json")
    parser.add_argument("--sequence-length", type=int, default=100)
    parser.add_argument("--top-k", type=int, default=3)
    parser.add_argument("--min-frames", type=int, default=20)
    parser.add_argument("--smooth-factor", type=float, default=0.7)
    return parser.parse_args()


def emit(payload):
    sys.stdout.write(json.dumps(payload, ensure_ascii=False) + "\n")
    sys.stdout.flush()


def decode_image(data_url):
    if not data_url:
        raise ValueError("Empty image payload")
    encoded = data_url.split(",", 1)[1] if "," in data_url else data_url
    image_bytes = base64.b64decode(encoded)
    image_array = np.frombuffer(image_bytes, dtype=np.uint8)
    frame = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
    if frame is None:
        raise ValueError("Failed to decode image frame")
    return frame


class RealtimeActionSession:
    def __init__(self, args):
        if not hasattr(batch.mp, "solutions") or not hasattr(batch.mp.solutions, "pose"):
            raise RuntimeError(
                "Current Python environment has incompatible mediapipe. Please install mediapipe==0.10.14 in the configured interpreter."
            )

        model_path = batch.resolve_local_path(args.model_path)
        class_mapping_path = batch.resolve_local_path(args.class_mapping)

        self.device = batch.torch.device("cuda" if batch.torch.cuda.is_available() else "cpu")
        self.class_names = batch.load_class_names(class_mapping_path)
        self.display_names = batch.build_display_names(self.class_names)
        self.model, config = batch.load_model(model_path, self.device)
        self.use_velocity = config.get("use_velocity", True)
        self.sequence_length = max(1, int(args.sequence_length))
        self.top_k = max(1, min(int(args.top_k), len(self.class_names)))
        self.min_frames = max(1, int(args.min_frames))
        self.smooth_factor = float(np.clip(args.smooth_factor, 0.0, 0.99))

        self.sequence_buffer = deque(maxlen=self.sequence_length)
        self.smoothed_scores = None
        self.score_history = []
        self.total_frames = 0
        self.pose_frames = 0
        self.pose_sequences = []
        self.pose_frame_indices = []
        self.pose = batch.mp.solutions.pose.Pose(
            static_image_mode=False,
            model_complexity=1,
            enable_segmentation=False,
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5,
        )

    def process_frame(self, frame):
        self.total_frames += 1
        pose_frame, _ = batch.extract_pose_sequence(frame, self.pose)
        if pose_frame is not None:
            self.pose_frames += 1
            self.sequence_buffer.append(pose_frame)
            self.pose_sequences.append(pose_frame)
            self.pose_frame_indices.append(self.total_frames)

        if len(self.sequence_buffer) < self.min_frames:
            return {
                "type": "pending",
                "capturedFrames": self.total_frames,
                "poseFrames": self.pose_frames,
                "requiredFrames": self.min_frames,
                "message": "Collecting stable pose frames",
            }

        model_input = batch.prepare_model_input(self.sequence_buffer, self.sequence_length, self.use_velocity)
        current_scores = batch.predict_scores(self.model, model_input, self.device)
        current_scores = batch.apply_benchpress_highjump_heuristic(current_scores, self.class_names, self.sequence_buffer)
        current_scores = batch.apply_benchpress_stillrings_heuristic(current_scores, self.class_names, self.sequence_buffer)
        current_scores = batch.apply_ropeclimbing_lunges_heuristic(current_scores, self.class_names, self.sequence_buffer)
        current_scores = batch.apply_upper_body_climb_heuristic(current_scores, self.class_names, self.sequence_buffer)

        if self.smoothed_scores is None:
            self.smoothed_scores = current_scores
        else:
            self.smoothed_scores = self.smooth_factor * self.smoothed_scores + (1.0 - self.smooth_factor) * current_scores

        self.score_history.append(self.smoothed_scores.copy())
        return {
            "type": "result",
            "data": self.build_result_payload(),
        }

    def build_result_payload(self):
        history_window = min(12, len(self.score_history))
        final_scores = np.mean(self.score_history[-history_window:], axis=0)
        final_scores = final_scores / np.clip(np.sum(final_scores), 1e-6, None)

        best_idx = int(np.argmax(final_scores))
        best_label = self.class_names[best_idx]
        best_score = float(final_scores[best_idx])
        structured_analysis = batch.build_structured_analysis(best_label, self.pose_sequences, self.pose_frame_indices)

        return {
            "success": True,
            "label": best_label,
            "labelZh": batch.ACTION_NAME_ZH.get(best_label, self.display_names[best_idx]),
            "score": round(best_score, 6),
            "scorePercent": int(round(best_score * 100)),
            "standard": best_score >= batch.LOW_SCORE_HINT_THRESHOLD,
            "hint": batch.build_hint(best_score, self.pose_frames, self.total_frames, structured_analysis),
            "suggestions": structured_analysis["advice"],
            "topPredictions": batch.serialize_predictions(self.class_names, self.display_names, final_scores, self.top_k),
            "poseFrames": self.pose_frames,
            "totalFrames": self.total_frames,
            "sequenceFrames": self.sequence_length,
            "source": "mediapipe-pytorch-live",
            "repetitions": structured_analysis["repetitions"],
            "currentPhase": structured_analysis["currentPhase"],
            "phaseTimeline": structured_analysis["phaseTimeline"],
            "jointAngles": structured_analysis["jointAngles"],
            "formChecks": structured_analysis["formChecks"],
        }

    def close(self):
        self.pose.close()


def main():
    os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")
    args = parse_args()
    session = RealtimeActionSession(args)
    emit(
        {
            "type": "ready",
            "message": "Realtime fitness vision ready",
            "requiredFrames": session.min_frames,
        }
    )

    try:
        for raw_line in sys.stdin:
            line = raw_line.strip()
            if not line:
                continue

            try:
                payload = json.loads(line)
                message_type = payload.get("type")

                if message_type == "ping":
                    emit({"type": "pong"})
                    continue

                if message_type == "close":
                    emit({"type": "closing"})
                    break

                if message_type != "frame":
                    emit({"type": "error", "message": f"Unsupported message type: {message_type}"})
                    continue

                frame = decode_image(payload.get("imageBase64", ""))
                emit(session.process_frame(frame))
            except Exception as exc:
                emit({"type": "error", "message": str(exc)})
    finally:
        session.close()


if __name__ == "__main__":
    main()
