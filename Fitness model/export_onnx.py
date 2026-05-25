"""
将 PyTorch 模型导出为 ONNX 格式
用于部署到 Spring Boot 项目
"""

import torch
import numpy as np
from train_model import ActionRecognitionModel
import os


def export_to_onnx():
    """导出模型为 ONNX 格式"""
    
    print("="*60)
    print("导出 PyTorch 模型为 ONNX 格式")
    print("="*60)
    
    # 加载模型
    model_path = './trained_models/best_model.pth'
    
    if not os.path.exists(model_path):
        print(f"❌ 模型文件不存在: {model_path}")
        print("请先运行: python evaluate_optimized.py 训练模型")
        return
    
    checkpoint = torch.load(model_path, map_location='cpu')
    config = checkpoint['config']
    
    # 创建模型
    input_size = config.get('input_size', 132)
    model = ActionRecognitionModel(
        input_size=input_size,
        hidden_size=config['hidden_size'],
        num_layers=config['num_layers'],
        num_classes=22,
        dropout=config['dropout']
    )
    
    model.load_state_dict(checkpoint['model_state_dict'])
    model.eval()
    
    print(f"✅ 模型加载成功")
    print(f"   Epoch: {checkpoint['epoch']}")
    print(f"   Val Acc: {checkpoint['val_acc']:.4f}")
    
    # 创建示例输入
    batch_size = 1
    seq_length = 100
    input_size = config.get('input_size', 132)
    dummy_input = torch.randn(batch_size, seq_length, input_size)
    
    # 导出为 ONNX
    onnx_path = './trained_models/fitness_action_model.onnx'
    
    print(f"\n开始导出 ONNX 模型...")
    
    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        export_params=True,
        opset_version=11,
        do_constant_folding=True,
        input_names=['input'],
        output_names=['output'],
        dynamic_axes={
            'input': {0: 'batch_size', 1: 'sequence_length'},
            'output': {0: 'batch_size'}
        }
    )
    
    print(f"✅ ONNX 模型导出成功!")
    print(f"   保存路径: {onnx_path}")
    print(f"   文件大小: {os.path.getsize(onnx_path) / 1024:.2f} KB")
    
    # 验证 ONNX 模型
    print("\n验证 ONNX 模型...")
    try:
        import onnx
        onnx_model = onnx.load(onnx_path)
        onnx.checker.check_model(onnx_model)
        print("✅ ONNX 模型验证通过!")
    except Exception as e:
        print(f"⚠️  ONNX 验证警告: {e}")
    
    print("\n" + "="*60)
    print("下一步：在 Spring Boot 中使用 ONNX Runtime")
    print("="*60)
    print("\nMaven 依赖:")
    print("""
<dependency>
    <groupId>com.microsoft.onnxruntime</groupId>
    <artifactId>onnxruntime</artifactId>
    <version>1.16.0</version>
</dependency>
    """)
    
    print("\nJava 代码示例:")
    print("""
import ai.onnxruntime.*;
import java.util.*;

public class FitnessActionRecognizer {
    private OrtSession session;
    
    public FitnessActionRecognizer(String modelPath) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        session = env.createSession(modelPath);
    }
    
    public int predict(float[][][] input) throws OrtException {
        // input shape: [1, 100, feature_dim]
        OnnxTensor tensor = OnnxTensor.createTensor(
            OrtEnvironment.getEnvironment(), 
            input
        );
        
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input", tensor);
        
        OrtResult result = session.run(inputs);
        float[][] output = (float[][]) result.get(0).getValue();
        
        // 获取概率最高的类别
        int predictedClass = 0;
        float maxProb = output[0][0];
        for (int i = 1; i < output[0].length; i++) {
            if (output[0][i] > maxProb) {
                maxProb = output[0][i];
                predictedClass = i;
            }
        }
        
        tensor.close();
        result.close();
        
        return predictedClass;
    }
    
    public void close() throws OrtException {
        session.close();
    }
}
    """)
    
    print("\n💡 提示：")
    print("   1. 将 fitness_action_model.onnx 复制到 Spring Boot 项目的 resources 目录")
    print("   2. 添加 ONNX Runtime Maven 依赖")
    print("   3. 使用上面的 Java 代码进行推理")


if __name__ == '__main__':
    try:
        import onnx
        export_to_onnx()
    except ImportError:
        print("安装 onnx 库...")
        import subprocess
        subprocess.check_call(['pip', 'install', 'onnx'])
        import onnx
        export_to_onnx()
