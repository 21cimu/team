"""
健身动作识别模型训练
基于骨架归一化 + 速度特征 + BiLSTM 注意力池化的序列分类模型
"""

import csv
import json
import os
import pickle
import random
from datetime import datetime
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, Dataset

# 检查是否有 GPU
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"使用设备: {device}")

POSE_XYZ_INDICES = [0, 1, 2]
VISIBILITY_INDEX = 3
LEFT_SHOULDER = 11
RIGHT_SHOULDER = 12
LEFT_HIP = 23
RIGHT_HIP = 24


def set_seed(seed):
    """固定随机种子，提升实验可复现性。"""
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def build_features(seq, add_velocity=True):
    """将原始骨架序列转换为更稳定的归一化时序特征。"""
    seq = np.asarray(seq, dtype=np.float32).copy()  # (T, 33, 4)

    coords = seq[:, :, POSE_XYZ_INDICES]
    visibility = seq[:, :, VISIBILITY_INDEX:VISIBILITY_INDEX + 1]

    hip_center = (coords[:, LEFT_HIP] + coords[:, RIGHT_HIP]) / 2.0
    shoulder_center = (coords[:, LEFT_SHOULDER] + coords[:, RIGHT_SHOULDER]) / 2.0

    centered_coords = coords - hip_center[:, None, :]

    torso_scale = np.linalg.norm(shoulder_center - hip_center, axis=1, keepdims=True)
    shoulder_width = np.linalg.norm(
        coords[:, LEFT_SHOULDER] - coords[:, RIGHT_SHOULDER], axis=1, keepdims=True
    )
    hip_width = np.linalg.norm(coords[:, LEFT_HIP] - coords[:, RIGHT_HIP], axis=1, keepdims=True)
    scale = np.maximum.reduce([torso_scale, shoulder_width, hip_width, np.full_like(torso_scale, 1e-3)])
    normalized_coords = centered_coords / scale[:, None, :]

    feature_parts = [normalized_coords.reshape(seq.shape[0], -1), visibility.reshape(seq.shape[0], -1)]

    if add_velocity:
        velocity = np.diff(normalized_coords, axis=0, prepend=normalized_coords[:1])
        feature_parts.append(velocity.reshape(seq.shape[0], -1))

    features = np.concatenate(feature_parts, axis=1).astype(np.float32)
    return features


def augment_sequence(features):
    """训练阶段的数据增强，强化时序鲁棒性。"""
    augmented = features.copy()

    if np.random.rand() < 0.9:
        augmented += np.random.normal(0.0, 0.01, augmented.shape).astype(np.float32)

    if np.random.rand() < 0.3:
        scale = np.random.uniform(0.95, 1.05)
        augmented[:, :99] *= scale
        if augmented.shape[1] >= 231:
            augmented[:, 132:231] *= scale

    if np.random.rand() < 0.3:
        shift = np.random.randint(-5, 6)
        augmented = np.roll(augmented, shift=shift, axis=0)

    if np.random.rand() < 0.25:
        dropout_mask = np.random.rand(*augmented.shape) > 0.03
        augmented *= dropout_mask.astype(np.float32)

    return augmented


def topk_accuracy(logits, labels, k=1):
    """计算 Top-k 准确率。"""
    topk = torch.topk(logits, k=min(k, logits.size(1)), dim=1).indices
    correct = topk.eq(labels.unsqueeze(1))
    return correct.any(dim=1).float().mean().item()


class FitnessDataset(Dataset):
    """健身动作数据集。"""

    def __init__(self, data_path, split="train", add_velocity=True):
        with open(data_path, "rb") as f:
            dataset = pickle.load(f)

        self.data = dataset[split]["data"]
        self.labels = torch.LongTensor(dataset[split]["labels"])
        self.is_train = split == "train"
        self.add_velocity = add_velocity

        sample_features = build_features(self.data[0], add_velocity=self.add_velocity)
        self.feature_dim = sample_features.shape[1]

        print(f"加载 {split} 集: {len(self.data)} 个样本, 特征维度: {self.feature_dim}")

    def __len__(self):
        return len(self.data)

    def __getitem__(self, idx):
        seq = self.data[idx]
        x = build_features(seq, add_velocity=self.add_velocity)

        if self.is_train:
            x = augment_sequence(x)

        y = self.labels[idx]
        return torch.FloatTensor(x), y


class TemporalAttentionPooling(nn.Module):
    """将整段动作序列压缩为更有判别力的表示。"""

    def __init__(self, feature_dim):
        super().__init__()
        self.score = nn.Sequential(
            nn.Linear(feature_dim, feature_dim // 2),
            nn.Tanh(),
            nn.Linear(feature_dim // 2, 1),
        )

    def forward(self, x):
        weights = torch.softmax(self.score(x), dim=1)
        return torch.sum(weights * x, dim=1)


class ActionRecognitionModel(nn.Module):
    """更强的时序骨架分类模型。"""

    def __init__(self, input_size=231, hidden_size=192, num_layers=2, num_classes=22, dropout=0.35):
        super().__init__()

        self.input_projection = nn.Sequential(
            nn.LayerNorm(input_size),
            nn.Linear(input_size, hidden_size),
            nn.GELU(),
            nn.Dropout(dropout * 0.5),
        )

        self.lstm = nn.LSTM(
            input_size=hidden_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            batch_first=True,
            dropout=dropout if num_layers > 1 else 0.0,
            bidirectional=True,
        )

        sequence_feature_dim = hidden_size * 2
        self.attention_pool = TemporalAttentionPooling(sequence_feature_dim)
        self.classifier = nn.Sequential(
            nn.LayerNorm(sequence_feature_dim * 3),
            nn.Linear(sequence_feature_dim * 3, hidden_size * 2),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_size * 2, hidden_size),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_size, num_classes),
        )

    def forward(self, x):
        x = self.input_projection(x)
        lstm_out, _ = self.lstm(x)

        attn_pool = self.attention_pool(lstm_out)
        avg_pool = lstm_out.mean(dim=1)
        max_pool = lstm_out.max(dim=1).values
        combined = torch.cat([attn_pool, avg_pool, max_pool], dim=1)
        return self.classifier(combined)


def evaluate_model(model, data_loader, criterion):
    """统一评估逻辑，返回 loss / Top-1 / Top-3。"""
    model.eval()
    total_loss = 0.0
    total_samples = 0
    total_top1 = 0.0
    total_top3 = 0.0

    with torch.no_grad():
        for batch_x, batch_y in data_loader:
            batch_x = batch_x.to(device, non_blocking=True)
            batch_y = batch_y.to(device, non_blocking=True)

            outputs = model(batch_x)
            loss = criterion(outputs, batch_y)

            batch_size = batch_y.size(0)
            total_loss += loss.item() * batch_size
            total_samples += batch_size
            total_top1 += topk_accuracy(outputs, batch_y, k=1) * batch_size
            total_top3 += topk_accuracy(outputs, batch_y, k=3) * batch_size

    return total_loss / total_samples, total_top1 / total_samples, total_top3 / total_samples


def create_class_weights(labels, num_classes):
    """用类别权重缓解少数类被忽略的问题。"""
    counts = np.bincount(labels, minlength=num_classes).astype(np.float32)
    weights = counts.sum() / np.maximum(counts, 1.0)
    weights = weights / weights.mean()
    return torch.FloatTensor(weights)


def build_ascii_table(headers, rows):
    """生成适合终端展示的 ASCII 表格。"""
    widths = [len(str(header)) for header in headers]
    for row in rows:
        for idx, cell in enumerate(row):
            widths[idx] = max(widths[idx], len(str(cell)))

    def format_row(row):
        return "| " + " | ".join(str(cell).ljust(widths[idx]) for idx, cell in enumerate(row)) + " |"

    separator = "+-" + "-+-".join("-" * width for width in widths) + "-+"
    lines = [separator, format_row(headers), separator]
    lines.extend(format_row(row) for row in rows)
    lines.append(separator)
    return "\n".join(lines)


def save_experiment_tables(save_dir, config, summary_rows, epoch_history):
    """保存实验结果表，便于直接写入报告。"""
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    save_path = Path(save_dir)
    save_path.mkdir(parents=True, exist_ok=True)

    history_csv_path = save_path / f"training_history_{timestamp}.csv"
    with open(history_csv_path, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=[
                "epoch",
                "train_loss",
                "train_top1",
                "train_top3",
                "val_loss",
                "val_top1",
                "val_top3",
                "learning_rate",
            ],
        )
        writer.writeheader()
        writer.writerows(epoch_history)

    summary_md_path = save_path / f"experiment_summary_{timestamp}.md"
    with open(summary_md_path, "w", encoding="utf-8") as f:
        f.write("# 实验结果汇总\n\n")
        f.write(f"- 生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"- 数据集: `{config['data_path']}`\n")
        f.write(f"- 模型目录: `{save_dir}`\n\n")
        f.write("## 结果表\n\n")
        f.write("| 指标 | 数值 |\n")
        f.write("| --- | --- |\n")
        for metric, value in summary_rows:
            f.write(f"| {metric} | {value} |\n")

        f.write("\n## 训练配置\n\n")
        f.write("| 参数 | 数值 |\n")
        f.write("| --- | --- |\n")
        for key in [
            "batch_size",
            "learning_rate",
            "num_epochs",
            "hidden_size",
            "num_layers",
            "dropout",
            "weight_decay",
            "label_smoothing",
            "use_velocity",
            "seed",
        ]:
            f.write(f"| {key} | {config[key]} |\n")

    return str(history_csv_path), str(summary_md_path)


def train_model():
    """训练模型。"""
    set_seed(42)

    config = {
        "data_path": "./processed_data/fitness_dataset.pkl",
        "batch_size": 48,
        "learning_rate": 8e-4,
        "num_epochs": 60,
        "hidden_size": 192,
        "num_layers": 2,
        "dropout": 0.35,
        "weight_decay": 5e-4,
        "label_smoothing": 0.05,
        "grad_clip": 1.0,
        "use_velocity": True,
        "save_dir": "./trained_models",
        "seed": 42,
    }

    os.makedirs(config["save_dir"], exist_ok=True)

    class_mapping_path = Path(config["data_path"]).parent / "class_mapping.json"
    with open(class_mapping_path, "r", encoding="utf-8") as f:
        class_mapping = json.load(f)
    num_classes = class_mapping["num_classes"]

    print(f"\n{'=' * 60}")
    print("健身动作识别模型训练")
    print(f"{'=' * 60}")
    print(f"类别数: {num_classes}")
    print(f"设备: {device}")
    print(f"批次大小: {config['batch_size']}")
    print(f"学习率: {config['learning_rate']}")
    print(f"训练轮数: {config['num_epochs']}")
    print(f"{'=' * 60}\n")

    print("\n加载数据集...")
    with open(config["data_path"], "rb") as f:
        dataset = pickle.load(f)

    if len(dataset["val"]["data"]) == 0 or len(dataset["test"]["data"]) == 0:
        raise ValueError("当前数据集缺少验证集或测试集，请先生成完整划分后再训练。")

    train_dataset = FitnessDataset(config["data_path"], split="train", add_velocity=config["use_velocity"])
    val_dataset = FitnessDataset(config["data_path"], split="val", add_velocity=config["use_velocity"])
    test_dataset = FitnessDataset(config["data_path"], split="test", add_velocity=config["use_velocity"])

    input_size = train_dataset.feature_dim
    config["input_size"] = input_size

    train_loader = DataLoader(
        train_dataset,
        batch_size=config["batch_size"],
        shuffle=True,
        num_workers=0,
        pin_memory=torch.cuda.is_available(),
    )
    val_loader = DataLoader(
        val_dataset,
        batch_size=config["batch_size"],
        shuffle=False,
        num_workers=0,
        pin_memory=torch.cuda.is_available(),
    )
    test_loader = DataLoader(
        test_dataset,
        batch_size=config["batch_size"],
        shuffle=False,
        num_workers=0,
        pin_memory=torch.cuda.is_available(),
    )

    print(f"\n输入特征维度: {input_size}")

    model = ActionRecognitionModel(
        input_size=input_size,
        hidden_size=config["hidden_size"],
        num_layers=config["num_layers"],
        num_classes=num_classes,
        dropout=config["dropout"],
    ).to(device)

    total_params = sum(p.numel() for p in model.parameters())
    trainable_params = sum(p.numel() for p in model.parameters() if p.requires_grad)
    print(f"\n模型参数量: {total_params:,}")
    print(f"可训练参数: {trainable_params:,}\n")

    class_weights = create_class_weights(dataset["train"]["labels"], num_classes).to(device)
    criterion = nn.CrossEntropyLoss(
        weight=class_weights,
        label_smoothing=config["label_smoothing"],
    )
    optimizer = optim.AdamW(
        model.parameters(),
        lr=config["learning_rate"],
        weight_decay=config["weight_decay"],
    )
    scheduler = optim.lr_scheduler.OneCycleLR(
        optimizer,
        max_lr=config["learning_rate"],
        epochs=config["num_epochs"],
        steps_per_epoch=len(train_loader),
        pct_start=0.15,
        anneal_strategy="cos",
        div_factor=10.0,
        final_div_factor=50.0,
    )

    best_val_acc = 0.0
    best_val_top3 = 0.0
    best_epoch = -1
    epoch_history = []

    print("开始训练...\n")

    for epoch in range(config["num_epochs"]):
        model.train()
        train_loss = 0.0
        train_top1_count = 0
        train_top3_count = 0
        train_total = 0

        for batch_x, batch_y in train_loader:
            batch_x = batch_x.to(device, non_blocking=True)
            batch_y = batch_y.to(device, non_blocking=True)

            optimizer.zero_grad(set_to_none=True)
            outputs = model(batch_x)
            loss = criterion(outputs, batch_y)
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(), max_norm=config["grad_clip"])
            optimizer.step()
            scheduler.step()

            batch_size = batch_y.size(0)
            train_loss += loss.item() * batch_size
            train_total += batch_size
            train_top1_count += (outputs.argmax(dim=1) == batch_y).sum().item()
            train_top3_count += (
                torch.topk(outputs, k=min(3, outputs.size(1)), dim=1).indices.eq(batch_y.unsqueeze(1)).any(dim=1)
            ).sum().item()

        train_loss /= train_total
        train_acc = train_top1_count / train_total
        train_top3 = train_top3_count / train_total

        val_loss, val_acc, val_top3 = evaluate_model(model, val_loader, criterion)

        current_lr = optimizer.param_groups[0]["lr"]
        epoch_history.append(
            {
                "epoch": epoch + 1,
                "train_loss": round(train_loss, 6),
                "train_top1": round(train_acc, 6),
                "train_top3": round(train_top3, 6),
                "val_loss": round(val_loss, 6),
                "val_top1": round(val_acc, 6),
                "val_top3": round(val_top3, 6),
                "learning_rate": round(current_lr, 8),
            }
        )
        print(f"Epoch [{epoch + 1}/{config['num_epochs']}]")
        print(
            f"  Train Loss: {train_loss:.4f}, Train Top-1: {train_acc:.4f}, "
            f"Train Top-3: {train_top3:.4f}"
        )
        print(
            f"  Val Loss:   {val_loss:.4f}, Val Top-1:   {val_acc:.4f}, "
            f"Val Top-3:   {val_top3:.4f}, LR: {current_lr:.6f}"
        )

        if val_acc > best_val_acc:
            best_val_acc = val_acc
            best_val_top3 = val_top3
            best_epoch = epoch + 1

            model_path = os.path.join(config["save_dir"], "best_model.pth")
            torch.save(
                {
                    "epoch": epoch + 1,
                    "model_state_dict": model.state_dict(),
                    "optimizer_state_dict": optimizer.state_dict(),
                    "val_loss": val_loss,
                    "val_acc": val_acc,
                    "val_top3": val_top3,
                    "config": config,
                },
                model_path,
            )
            print(f"  ✅ 保存最佳模型 (Val Top-1: {val_acc:.4f})\n")
        else:
            print()

    print("\n加载最佳模型进行测试...")
    model_path = os.path.join(config["save_dir"], "best_model.pth")
    checkpoint = torch.load(model_path, map_location=device)
    model.load_state_dict(checkpoint["model_state_dict"])

    test_loss, test_acc, test_top3 = evaluate_model(model, test_loader, criterion)

    print(f"\n{'=' * 60}")
    print("训练完成!")
    print(f"{'=' * 60}")
    print(f"最佳模型轮数: {best_epoch}")
    print(f"最佳验证 Top-1: {best_val_acc:.4f}")
    print(f"最佳验证 Top-3: {best_val_top3:.4f}")
    print(f"测试 Loss: {test_loss:.4f}")
    print(f"测试 Top-1: {test_acc:.4f}")
    print(f"测试 Top-3: {test_top3:.4f}")
    print(f"模型保存位置: {config['save_dir']}")
    print(f"{'=' * 60}")

    summary_rows = [
        ("最佳模型轮数", best_epoch),
        ("最佳验证 Top-1", f"{best_val_acc:.2%}"),
        ("最佳验证 Top-3", f"{best_val_top3:.2%}"),
        ("测试 Loss", f"{test_loss:.4f}"),
        ("测试 Top-1", f"{test_acc:.2%}"),
        ("测试 Top-3", f"{test_top3:.2%}"),
    ]
    print("\n实验结果表:")
    print(build_ascii_table(["指标", "数值"], summary_rows))

    history_csv_path, summary_md_path = save_experiment_tables(
        config["save_dir"],
        config,
        summary_rows,
        epoch_history,
    )
    print("\n表格文件已生成:")
    print(f"  - 训练历史 CSV: {history_csv_path}")
    print(f"  - 实验汇总 MD:  {summary_md_path}\n")

    return {
        "best_epoch": best_epoch,
        "best_val_acc": best_val_acc,
        "best_val_top3": best_val_top3,
        "test_loss": test_loss,
        "test_acc": test_acc,
        "test_top3": test_top3,
        "model_path": model_path,
        "history_csv_path": history_csv_path,
        "summary_md_path": summary_md_path,
    }


if __name__ == "__main__":
    train_model()
