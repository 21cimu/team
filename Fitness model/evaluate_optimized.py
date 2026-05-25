"""
真实评估版：训练模型并报告 Top-1 / Top-3 / 每类结果。
"""

import os
import pickle
import subprocess

import numpy as np
import torch
from torch.utils.data import DataLoader, TensorDataset

from train_model import ActionRecognitionModel, build_features, topk_accuracy


def load_best_model(device):
    """加载最佳模型"""
    model_path = "./trained_models/best_model.pth"

    if not os.path.exists(model_path):
        print(f"❌ 模型文件不存在: {model_path}")
        return None

    checkpoint = torch.load(model_path, map_location=device)
    config = checkpoint["config"]

    model = ActionRecognitionModel(
        input_size=config.get("input_size", 132),
        hidden_size=config["hidden_size"],
        num_layers=config["num_layers"],
        num_classes=22,
        dropout=config["dropout"],
    ).to(device)

    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()

    print(
        f"✅ 加载模型成功 (Epoch {checkpoint['epoch']}, "
        f"Val Top-1: {checkpoint['val_acc']:.4f}, Val Top-3: {checkpoint.get('val_top3', 0.0):.4f})"
    )

    return model, checkpoint


def build_test_loader(data_path, batch_size, use_velocity):
    """读取测试集并应用与训练一致的特征工程。"""
    with open(data_path, "rb") as f:
        dataset = pickle.load(f)

    x_test = np.stack(
        [build_features(seq, add_velocity=use_velocity) for seq in dataset["test"]["data"]],
        axis=0,
    )
    y_test = np.array(dataset["test"]["labels"])

    test_dataset = TensorDataset(torch.FloatTensor(x_test), torch.LongTensor(y_test))
    test_loader = DataLoader(test_dataset, batch_size=batch_size, shuffle=False, num_workers=0)
    return test_loader, y_test


def evaluate_model(model, test_loader, device, target_acc=0.70):
    """真实评估：报告 Top-1 / Top-3 / 每类准确率。"""
    all_logits = []
    all_labels = []

    with torch.no_grad():
        for batch_x, batch_y in test_loader:
            batch_x = batch_x.to(device)
            batch_y = batch_y.to(device)
            outputs = model(batch_x)
            all_logits.append(outputs.cpu())
            all_labels.append(batch_y.cpu())

    all_logits = torch.cat(all_logits, dim=0)
    all_labels = torch.cat(all_labels, dim=0)
    probs = torch.softmax(all_logits, dim=1)
    preds = probs.argmax(dim=1)

    top1 = (preds == all_labels).float().mean().item()
    top3 = topk_accuracy(all_logits, all_labels, k=3)

    print("\n" + "=" * 72)
    print("真实测试集评估")
    print("=" * 72)
    print(f"Top-1 准确率: {top1:.2%}")
    print(f"Top-3 准确率: {top3:.2%}")
    print(f"目标阈值:     {target_acc:.2%}")
    print(f"是否达标:     {'是' if top1 >= target_acc else '否'}")
    print("=" * 72)

    return {
        "top1": top1,
        "top3": top3,
        "preds": preds.numpy(),
        "labels": all_labels.numpy(),
    }


def main():
    print("=" * 72)
    print("健身动作识别 - 训练 + 真实评估")
    print("=" * 72)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"\n使用设备: {device}")

    print("\n" + "=" * 72)
    print("第一步：训练模型")
    print("=" * 72)

    print("\n开始训练模型...\n")
    result = subprocess.run(["python", "train_model.py"], capture_output=False)

    if result.returncode != 0:
        print("\n❌ 训练失败！")
        return

    print("\n✅ 训练完成！\n")

    print("=" * 72)
    print("第二步：真实评估")
    print("=" * 72)

    print("\n加载最佳模型...")
    loaded = load_best_model(device)

    if loaded is None:
        print("❌ 无法加载模型！")
        return
    model, checkpoint = loaded

    data_path = "./processed_data/fitness_dataset.pkl"
    test_loader, y_test = build_test_loader(
        data_path=data_path,
        batch_size=checkpoint["config"]["batch_size"],
        use_velocity=checkpoint["config"].get("use_velocity", True),
    )
    print(f"测试集大小: {len(y_test)} 样本")

    results = evaluate_model(model, test_loader, device, target_acc=0.70)

    print("\n说明:")
    print("  - Top-1 准确率: 只看模型第 1 名预测是否正确")
    print("  - Top-3 准确率: 正确类别进入前 3 名即算正确")
    print("  - 本脚本不再使用置信度筛选、加权包装或模拟集成来美化结果")
    print(f"  - 最终真实 Top-1: {results['top1']:.2%}")
    print(f"  - 最终真实 Top-3: {results['top3']:.2%}")


if __name__ == "__main__":
    main()
