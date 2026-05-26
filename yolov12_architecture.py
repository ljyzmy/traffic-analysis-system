import streamlit as st
import graphviz
import os

# 页面配置
st.set_page_config(page_title="YOLOv12详细架构图", layout="wide")

# 标题
st.title("YOLOv12 详细架构图")
st.markdown("""
基于YOLOv11架构改进的YOLOv12模型，引入了区域注意力机制(Area Attention)和残差高效层聚合网络(R-ELAN)。
""")

# 确保可以使用Graphviz
os.environ["PATH"] += os.pathsep + 'C:/Program Files/Graphviz/bin/'

# 创建选项卡
tab1, tab2, tab3, tab4 = st.tabs(["YOLOv12完整架构", "A2C2f (R-ELAN)详细结构", "区域注意力机制", "YOLOv11与YOLOv12对比"])

with tab1:
    st.header("YOLOv12 完整网络架构")
    
    def create_yolov12_architecture():
        dot = graphviz.Digraph(comment='YOLOv12 Architecture', format='png')
        dot.attr(rankdir='TB', size='14,10', ratio='fill')
        
        # 主干网络部分
        with dot.subgraph(name='cluster_backbone') as c:
            c.attr(label='骨干网络 (Backbone)')
            c.node('input', '输入\n640×640×3', shape='ellipse', style='filled', fillcolor='lightgrey')
            c.node('conv1', 'Conv\n3→96, 3×3, s=2', shape='box', style='filled', fillcolor='orange')
            c.node('conv2', 'Conv\n96→192, 3×3, s=2', shape='box', style='filled', fillcolor='orange')
            c.node('c3k1', 'C3k2\n192→384, n=2', shape='box', style='filled', fillcolor='lightblue')
            c.node('conv3', 'Conv\n384→384, 3×3, s=2', shape='box', style='filled', fillcolor='orange')
            c.node('c3k2', 'C3k2\n384→768, n=2', shape='box', style='filled', fillcolor='lightblue')
            c.node('conv4', 'Conv\n768→768, 3×3, s=2', shape='box', style='filled', fillcolor='orange')
            c.node('c3k3', 'C3k2\n768→768, n=2', shape='box', style='filled', fillcolor='lightblue')
            c.node('conv5', 'Conv\n768→768, 3×3, s=2', shape='box', style='filled', fillcolor='orange')
            c.node('c3k4', 'C3k2\n768→768, n=2', shape='box', style='filled', fillcolor='lightblue')
            
            c.edge('input', 'conv1')
            c.edge('conv1', 'conv2')
            c.edge('conv2', 'c3k1')
            c.edge('c3k1', 'conv3')
            c.edge('conv3', 'c3k2')
            c.edge('c3k2', 'conv4')
            c.edge('conv4', 'c3k3')
            c.edge('c3k3', 'conv5')
            c.edge('conv5', 'c3k4')
        
        # 特征金字塔网络部分
        with dot.subgraph(name='cluster_neck') as c:
            c.attr(label='颈部网络 (Neck)')
            c.node('sppf', 'SPPF\n768→768, k=5', shape='box', style='filled', fillcolor='lightgreen')
            c.node('a2c2f', 'A2C2f\n768→768, n=2', shape='box', style='filled', fillcolor='yellow')
            c.node('upsample1', 'Upsample\ns=2', shape='box', style='filled', fillcolor='red')
            c.node('concat1', 'Concat', shape='box', style='filled', fillcolor='pink')
            c.node('a2c2f2', 'A2C2f\n1536→768, n=2', shape='box', style='filled', fillcolor='yellow')
            c.node('upsample2', 'Upsample\ns=2', shape='box', style='filled', fillcolor='red')
            c.node('concat2', 'Concat', shape='box', style='filled', fillcolor='pink')
            c.node('a2c2f3', 'A2C2f\n1152→384, n=2', shape='box', style='filled', fillcolor='yellow')
            
            c.edge('c3k4', 'sppf')
            c.edge('sppf', 'a2c2f')
            c.edge('a2c2f', 'upsample1')
            c.edge('upsample1', 'concat1')
            c.edge('c3k3', 'concat1')
            c.edge('concat1', 'a2c2f2')
            c.edge('a2c2f2', 'upsample2')
            c.edge('upsample2', 'concat2')
            c.edge('c3k2', 'concat2')
            c.edge('concat2', 'a2c2f3')
        
        # 检测头部分
        with dot.subgraph(name='cluster_head') as c:
            c.attr(label='检测头 (Detection Head)')
            c.node('conv6', 'Conv\n384→384, 3×3, s=2', shape='box', style='filled', fillcolor='orange')
            c.node('concat3', 'Concat', shape='box', style='filled', fillcolor='pink')
            c.node('a2c2f4', 'A2C2f\n1152→768, n=2', shape='box', style='filled', fillcolor='yellow')
            c.node('conv7', 'Conv\n768→768, 3×3, s=2', shape='box', style='filled', fillcolor='orange')
            c.node('concat4', 'Concat', shape='box', style='filled', fillcolor='pink')
            c.node('a2c2f5', 'A2C2f\n1536→768, n=2', shape='box', style='filled', fillcolor='yellow')
            c.node('detect', 'Detect\n[384, 768, 768]', shape='box', style='filled', fillcolor='cyan')
            
            c.edge('a2c2f3', 'conv6')
            c.edge('conv6', 'concat3')
            c.edge('a2c2f2', 'concat3')
            c.edge('concat3', 'a2c2f4')
            c.edge('a2c2f4', 'conv7')
            c.edge('conv7', 'concat4')
            c.edge('a2c2f', 'concat4')
            c.edge('concat4', 'a2c2f5')
            c.edge('a2c2f3', 'detect')
            c.edge('a2c2f4', 'detect')
            c.edge('a2c2f5', 'detect')
            
        # 添加FPN连接的视觉指示
        dot.attr('edge', style='dashed', color='blue', penwidth='2')
        dot.edge('c3k2', 'concat2', constraint='false')
        dot.edge('c3k3', 'concat1', constraint='false')
        
        # 添加PAN连接的视觉指示
        dot.attr('edge', style='dashed', color='red', penwidth='2')
        dot.edge('a2c2f2', 'concat3', constraint='false')
        dot.edge('a2c2f', 'concat4', constraint='false')
        
        # 图例
        with dot.subgraph(name='cluster_legend') as c:
            c.attr(label='图例')
            c.node('legend_conv', 'Conv', shape='box', style='filled', fillcolor='orange')
            c.node('legend_c3k', 'C3k2', shape='box', style='filled', fillcolor='lightblue')
            c.node('legend_sppf', 'SPPF', shape='box', style='filled', fillcolor='lightgreen')
            c.node('legend_a2c2f', 'A2C2f (R-ELAN)', shape='box', style='filled', fillcolor='yellow')
            c.node('legend_upsample', 'Upsample', shape='box', style='filled', fillcolor='red')
            c.node('legend_concat', 'Concat', shape='box', style='filled', fillcolor='pink')
            c.node('legend_detect', 'Detect', shape='box', style='filled', fillcolor='cyan')
            c.node('legend_fpn', 'FPN连接', shape='plaintext')
            c.edge('legend_fpn', 'legend_fpn', style='dashed', color='blue', penwidth='2')
            c.node('legend_pan', 'PAN连接', shape='plaintext')
            c.edge('legend_pan', 'legend_pan', style='dashed', color='red', penwidth='2')
        
        return dot

    yolov12_arch = create_yolov12_architecture()
    st.graphviz_chart(yolov12_arch)
    
    st.markdown("""
    **YOLOv12完整架构特点：**
    
    1. **骨干网络**：采用轻量级的CNN结构，通过多层C3k2块提取特征
    2. **颈部网络**：使用A2C2f(R-ELAN)模块代替传统CSP结构，增强特征表达能力
    3. **特征融合**：结合FPN和PAN结构进行多尺度特征融合
    4. **检测头**：支持小、中、大三种尺度的目标检测
    """)

with tab2:
    st.header("A2C2f (R-ELAN) 模块详细结构")
    
    def create_a2c2f_module():
        dot = graphviz.Digraph(comment='A2C2f (R-ELAN) Module', format='png')
        dot.attr(rankdir='LR', size='12,8')
        
        # 节点定义
        dot.node('input', '输入特征 (c1)', shape='box', style='filled', fillcolor='lightgrey')
        dot.node('cv1', 'Conv 1×1\nc1→c_', shape='box', style='filled', fillcolor='orange')
        dot.node('m0', '原始特征', shape='box', style='filled', fillcolor='lightgrey')
        
        # ABlock区域
        with dot.subgraph(name='cluster_ablock1') as c:
            c.attr(label='ABlock 1')
            c.node('a1_ln', 'LayerNorm', shape='box', style='filled', fillcolor='lightgrey')
            c.node('a1_attn', '区域注意力\n(Area Attention)', shape='box', style='filled', fillcolor='lightblue')
            c.node('a1_add1', '+', shape='circle', style='filled', fillcolor='lightgrey')
            c.node('a1_ln2', 'LayerNorm', shape='box', style='filled', fillcolor='lightgrey')
            c.node('a1_mlp', 'MLP\n扩展比例=1.2', shape='box', style='filled', fillcolor='lightpink')
            c.node('a1_add2', '+', shape='circle', style='filled', fillcolor='lightgrey')
            
            c.edge('a1_ln', 'a1_attn')
            c.edge('a1_attn', 'a1_add1')
            c.edge('a1_ln', 'a1_add1', style='dashed')
            c.edge('a1_add1', 'a1_ln2')
            c.edge('a1_ln2', 'a1_mlp')
            c.edge('a1_mlp', 'a1_add2')
            c.edge('a1_add1', 'a1_add2', style='dashed')
            
        with dot.subgraph(name='cluster_ablock2') as c:
            c.attr(label='ABlock 2')
            c.node('a2_ln', 'LayerNorm', shape='box', style='filled', fillcolor='lightgrey')
            c.node('a2_attn', '区域注意力\n(Area Attention)', shape='box', style='filled', fillcolor='lightblue')
            c.node('a2_add1', '+', shape='circle', style='filled', fillcolor='lightgrey')
            c.node('a2_ln2', 'LayerNorm', shape='box', style='filled', fillcolor='lightgrey')
            c.node('a2_mlp', 'MLP\n扩展比例=1.2', shape='box', style='filled', fillcolor='lightpink')
            c.node('a2_add2', '+', shape='circle', style='filled', fillcolor='lightgrey')
            
            c.edge('a2_ln', 'a2_attn')
            c.edge('a2_attn', 'a2_add1')
            c.edge('a2_ln', 'a2_add1', style='dashed')
            c.edge('a2_add1', 'a2_ln2')
            c.edge('a2_ln2', 'a2_mlp')
            c.edge('a2_mlp', 'a2_add2')
            c.edge('a2_add1', 'a2_add2', style='dashed')
        
        dot.node('concat', 'Concat', shape='box', style='filled', fillcolor='pink')
        dot.node('cv2', 'Conv 1×1\n(n+1)×c_→c2', shape='box', style='filled', fillcolor='orange')
        dot.node('gamma', '缩放系数 γ', shape='box', style='filled', fillcolor='yellow')
        dot.node('add', '+', shape='circle', style='filled', fillcolor='lightgrey')
        dot.node('output', '输出特征 (c2)', shape='box', style='filled', fillcolor='lightgrey')
        
        # 边定义
        dot.edge('input', 'cv1')
        dot.edge('cv1', 'm0')
        dot.edge('m0', 'a1_ln')
        dot.edge('a1_add2', 'a2_ln')
        
        dot.edge('m0', 'concat')
        dot.edge('a1_add2', 'concat')
        dot.edge('a2_add2', 'concat')
        
        dot.edge('concat', 'cv2')
        dot.edge('cv2', 'gamma')
        dot.edge('gamma', 'add')
        dot.edge('input', 'add', label='残差连接')
        dot.edge('add', 'output')
        
        return dot

    a2c2f_module = create_a2c2f_module()
    st.graphviz_chart(a2c2f_module)
    
    st.markdown("""
    **R-ELAN模块特点：**
    
    1. **块级结构**：包含多个ABlock块，每个块内部具有标准的Transformer架构设计
    2. **高效设计**：
       - 使用1x1卷积进行通道数调整，降低计算开销
       - 精简的MLP扩展比例(1.2)，相比传统Transformer的4倍扩展显著降低参数量
       - 多路特征汇聚设计，保留更多中间特征信息
    3. **残差机制优化**：
       - 添加带缩放系数γ的残差连接，优化梯度流
       - 内部采用标准的Transformer残差连接结构
    4. **与YOLOv11对比**：
       - 替换YOLOv11中的Bottleneck和C3k模块
       - 更强的表征能力，特别是对小目标和复杂场景
    """)

with tab3:
    st.header("区域注意力机制 (Area Attention) 详细结构")
    
    def create_area_attention_module():
        dot = graphviz.Digraph(comment='Area Attention Module', format='png')
        dot.attr(rankdir='TB', size='12,8')
        
        # 节点定义
        dot.node('input', '输入特征图\n(B,C,H,W)', shape='box', style='filled', fillcolor='lightgrey')
        dot.node('reshape', '特征重塑\n(B,H×W,C)', shape='box', style='filled', fillcolor='lightgrey')
        dot.node('qkv', 'QKV投影\nLinear层', shape='box', style='filled', fillcolor='orange')
        
        # 区域划分
        with dot.subgraph(name='cluster_areas') as c:
            c.attr(label='区域划分 (area=4)')
            c.node('split', '区域分割', shape='box', style='filled', fillcolor='lightgrey')
            c.node('area1', '区域1\n(B,H×W/4,C)', shape='box', style='filled', fillcolor='lightblue')
            c.node('area2', '区域2\n(B,H×W/4,C)', shape='box', style='filled', fillcolor='lightblue')
            c.node('area3', '区域3\n(B,H×W/4,C)', shape='box', style='filled', fillcolor='lightblue')
            c.node('area4', '区域4\n(B,H×W/4,C)', shape='box', style='filled', fillcolor='lightblue')
        
        # 注意力计算
        with dot.subgraph(name='cluster_attention') as c:
            c.attr(label='注意力计算')
            c.node('q1', 'Q', shape='box', style='filled', fillcolor='yellow')
            c.node('k1', 'K', shape='box', style='filled', fillcolor='yellow')
            c.node('v1', 'V', shape='box', style='filled', fillcolor='yellow')
            c.node('attn1', 'Q·K^T/√d', shape='box', style='filled', fillcolor='yellow')
            c.node('soft1', 'Softmax', shape='box', style='filled', fillcolor='yellow')
            c.node('out1', '·V', shape='box', style='filled', fillcolor='yellow')
            
            c.edge('q1', 'attn1')
            c.edge('k1', 'attn1')
            c.edge('attn1', 'soft1')
            c.edge('soft1', 'out1')
            c.edge('v1', 'out1')
        
        dot.node('merge', '区域合并', shape='box', style='filled', fillcolor='lightgrey')
        dot.node('reshape2', '特征重塑\n(B,C,H,W)', shape='box', style='filled', fillcolor='lightgrey')
        dot.node('pe', '位置感知器\nConv 7×7', shape='box', style='filled', fillcolor='lightgreen')
        dot.node('proj', '特征投影\nConv 1×1', shape='box', style='filled', fillcolor='orange')
        dot.node('output', '输出特征图', shape='box', style='filled', fillcolor='lightgrey')
        
        # 边定义
        dot.edge('input', 'reshape')
        dot.edge('reshape', 'qkv')
        dot.edge('qkv', 'split')
        dot.edge('split', 'area1')
        dot.edge('split', 'area2')
        dot.edge('split', 'area3')
        dot.edge('split', 'area4')
        
        dot.edge('area1', 'q1')
        dot.edge('area1', 'k1')
        dot.edge('area1', 'v1')
        
        # 其他区域的注意力计算使用虚线表示
        dot.attr('edge', style='dashed')
        dot.edge('area2', 'q1')
        dot.edge('area3', 'q1')
        dot.edge('area4', 'q1')
        dot.attr('edge', style='solid')
        
        dot.edge('out1', 'merge')
        dot.edge('merge', 'reshape2')
        dot.edge('reshape2', 'pe')
        dot.edge('pe', 'proj')
        dot.edge('proj', 'output')
        
        # 添加FlashAttention优化
        dot.node('flash', 'FlashAttention\n优化计算', shape='note', style='filled', fillcolor='gold')
        dot.edge('attn1', 'flash', style='dashed', arrowhead='none')
        
        return dot

    area_attention = create_area_attention_module()
    st.graphviz_chart(area_attention)
    
    st.markdown("""
    **区域注意力机制特点：**
    
    1. **区域划分策略**：
       - 将特征图划分为多个等大小区域(默认4个)
       - 每个区域内独立计算自注意力，显著降低计算复杂度
       - 保持注意力的局部上下文感知能力
    
    2. **位置编码改进**：
       - 使用7×7可分离卷积作为"位置感知器"(Position Sensor)
       - 替代传统位置编码，更好地捕捉空间位置信息
       - 隐式学习位置关系，无需显式的位置编码
    
    3. **计算优化**：
       - 支持FlashAttention加速计算
       - 相比全局注意力，计算复杂度从O(n²)降低到O(n²/k)，k为区域数量
    
    4. **与传统注意力的区别**：
       - 传统自注意力：所有像素点之间计算注意力
       - 区域注意力：仅在划分区域内计算注意力，降低计算量
       - 保持注意力机制的核心优势，同时提高计算效率
    """)

with tab4:
    st.header("YOLOv11 与 YOLOv12 对比")
    
    def create_comparison_chart():
        dot = graphviz.Digraph(comment='YOLOv11 vs YOLOv12 Comparison', format='png')
        dot.attr(rankdir='LR', size='14,8')
        
        # YOLOv11 部分
        with dot.subgraph(name='cluster_yolov11') as c:
            c.attr(label='YOLOv11 核心模块')
            
            # C2PSA 模块
            with c.subgraph(name='cluster_c2psa') as c2:
                c2.attr(label='C2PSA 模块')
                c2.node('v11_conv1', 'Conv', shape='box', style='filled', fillcolor='orange')
                c2.node('v11_psablock1', 'PSABlock', shape='box', style='filled', fillcolor='brown')
                c2.node('v11_psablock2', 'PSABlock', shape='box', style='filled', fillcolor='brown')
                c2.node('v11_concat', 'Concat', shape='box', style='filled', fillcolor='pink')
                c2.node('v11_conv2', 'Conv', shape='box', style='filled', fillcolor='orange')
                
                c2.edge('v11_conv1', 'v11_psablock1')
                c2.edge('v11_psablock1', 'v11_psablock2')
                c2.edge('v11_conv1', 'v11_concat')
                c2.edge('v11_psablock1', 'v11_concat')
                c2.edge('v11_psablock2', 'v11_concat')
                c2.edge('v11_concat', 'v11_conv2')
            
            # C3k 模块
            with c.subgraph(name='cluster_c3k') as c3:
                c3.attr(label='C3k 模块')
                c3.node('v11_c3k_conv1', 'Conv', shape='box', style='filled', fillcolor='orange')
                c3.node('v11_c3k_split', 'Split', shape='box', style='filled', fillcolor='lightgrey')
                c3.node('v11_c3k_bottleneck1', 'Bottleneck', shape='box', style='filled', fillcolor='grey')
                c3.node('v11_c3k_bottleneck2', 'Bottleneck', shape='box', style='filled', fillcolor='grey')
                c3.node('v11_c3k_concat', 'Concat', shape='box', style='filled', fillcolor='pink')
                c3.node('v11_c3k_conv2', 'Conv', shape='box', style='filled', fillcolor='orange')
                
                c3.edge('v11_c3k_conv1', 'v11_c3k_split')
                c3.edge('v11_c3k_split', 'v11_c3k_bottleneck1')
                c3.edge('v11_c3k_bottleneck1', 'v11_c3k_bottleneck2')
                c3.edge('v11_c3k_split', 'v11_c3k_concat')
                c3.edge('v11_c3k_bottleneck2', 'v11_c3k_concat')
                c3.edge('v11_c3k_concat', 'v11_c3k_conv2')
            
            # 注意力部分
            with c.subgraph(name='cluster_v11_attention') as c4:
                c4.attr(label='注意力机制')
                c4.node('v11_attn_input', '输入特征', shape='box', style='filled', fillcolor='lightgrey')
                c4.node('v11_attn_conv', 'Conv', shape='box', style='filled', fillcolor='orange')
                c4.node('v11_attn_split', 'Split', shape='box', style='filled', fillcolor='lightgrey')
                c4.node('v11_attn_global', '全局注意力', shape='box', style='filled', fillcolor='red')
                c4.node('v11_attn_add', '+', shape='circle', style='filled', fillcolor='lightgrey')
                
                c4.edge('v11_attn_input', 'v11_attn_conv')
                c4.edge('v11_attn_conv', 'v11_attn_split')
                c4.edge('v11_attn_split', 'v11_attn_global')
                c4.edge('v11_attn_global', 'v11_attn_add')
                c4.edge('v11_attn_split', 'v11_attn_add')
        
        # YOLOv12 部分
        with dot.subgraph(name='cluster_yolov12') as c:
            c.attr(label='YOLOv12 核心模块')
            
            # A2C2f 模块
            with c.subgraph(name='cluster_a2c2f') as c2:
                c2.attr(label='A2C2f (R-ELAN) 模块')
                c2.node('v12_conv1', 'Conv 1×1', shape='box', style='filled', fillcolor='orange')
                c2.node('v12_ablock1', 'ABlock\n(Area Attention)', shape='box', style='filled', fillcolor='lightblue')
                c2.node('v12_ablock2', 'ABlock\n(Area Attention)', shape='box', style='filled', fillcolor='lightblue')
                c2.node('v12_concat', 'Concat', shape='box', style='filled', fillcolor='pink')
                c2.node('v12_conv2', 'Conv 1×1', shape='box', style='filled', fillcolor='orange')
                c2.node('v12_gamma', 'γ×', shape='circle', style='filled', fillcolor='yellow')
                c2.node('v12_add', '+', shape='circle', style='filled', fillcolor='lightgrey')
                
                c2.edge('v12_conv1', 'v12_ablock1')
                c2.edge('v12_ablock1', 'v12_ablock2')
                c2.edge('v12_conv1', 'v12_concat')
                c2.edge('v12_ablock1', 'v12_concat')
                c2.edge('v12_ablock2', 'v12_concat')
                c2.edge('v12_concat', 'v12_conv2')
                c2.edge('v12_conv2', 'v12_gamma')
                c2.edge('v12_gamma', 'v12_add')
                c2.edge('v12_add', 'v12_add', label='残差连接', tailport='w', headport='e')
            
            # Area Attention 模块
            with c.subgraph(name='cluster_area_attention') as c3:
                c3.attr(label='区域注意力 (Area Attention) 模块')
                c3.node('v12_aa_input', '输入特征', shape='box', style='filled', fillcolor='lightgrey')
                c3.node('v12_aa_qkv', 'QKV投影', shape='box', style='filled', fillcolor='orange')
                c3.node('v12_aa_areas', '区域划分 (×4)', shape='box', style='filled', fillcolor='lightblue')
                c3.node('v12_aa_attention', '局部注意力计算', shape='box', style='filled', fillcolor='yellow')
                c3.node('v12_aa_pe', '位置感知器\n(7×7 Conv)', shape='box', style='filled', fillcolor='lightgreen')
                c3.node('v12_aa_proj', '特征投影', shape='box', style='filled', fillcolor='orange')
                
                c3.edge('v12_aa_input', 'v12_aa_qkv')
                c3.edge('v12_aa_qkv', 'v12_aa_areas')
                c3.edge('v12_aa_areas', 'v12_aa_attention')
                c3.edge('v12_aa_attention', 'v12_aa_pe')
                c3.edge('v12_aa_pe', 'v12_aa_proj')
        
        return dot

    comparison_chart = create_comparison_chart()
    st.graphviz_chart(comparison_chart)
    
    # 创建表格对比
    st.subheader("YOLOv11与YOLOv12主要特性对比")
    
    data = {
        "特性": ["基本架构", "注意力机制", "核心模块", "MLP扩展比例", "位置编码", "计算复杂度", "小目标检测效果", "参数量", "推理速度"],
        "YOLOv11": ["CNN为主，部分注意力", "全局注意力", "C2PSA, C3k, Bottleneck", "4", "传统位置编码", "较高", "中等", "中等", "中等"],
        "YOLOv12": ["注意力为中心", "区域注意力", "A2C2f (R-ELAN), ABlock", "1.2", "7×7卷积位置感知器", "降低约75%", "优秀", "轻量级", "较快"]
    }
    
    st.table(data)
    
    st.markdown("""
    **YOLOv11和YOLOv12主要区别：**
    
    1. **基本架构转变**:
       - YOLOv11: 仍主要基于CNN，C2PSA模块引入部分注意力机制
       - YOLOv12: 以注意力为中心，引入更高效的区域注意力和R-ELAN
    
    2. **注意力机制优化**:
       - YOLOv11: 使用全局注意力，计算复杂度高
       - YOLOv12: 使用区域注意力机制，通过区域划分降低计算开销约75%
    
    3. **网络结构优化**:
       - YOLOv11: 使用Bottleneck和C3k模块
       - YOLOv12: 使用A2C2f(R-ELAN)模块，多路特征融合提升表达能力
       
    4. **性能提升**:
       - YOLOv12-n在COCO数据集上达到40.6% mAP，比YOLOv11-n提高1.2%
       - 推理速度提升，同等精度下比RT-DETRv2-r18快42%
    """)
    
# 添加页脚信息
st.markdown("---")
st.markdown("### YOLOv12的技术优势")

st.markdown("""
1. **更高的检测精度**：YOLOv12-n在COCO数据集上达到了40.6% mAP，比YOLOv10-n和YOLOv11-n分别提高2.1%和1.2%。

2. **计算效率优化**：尽管引入了注意力机制，YOLOv12通过区域注意力和优化的MLP比例，保持了与之前版本相当的推理速度。

3. **更灵活的模型架构**：支持从边缘设备到高性能服务器的多种部署场景。

4. **与RT-DETR相比的优势**：YOLOv12-s比RT-DETRv2-r18运行速度快42%，同时使用更少的参数和计算资源。
""")

# 添加运行按钮和下载选项
if st.button("生成高清架构图"):
    with st.spinner("正在生成高清架构图..."):
        # 创建高清版本
        yolov12_hd = create_yolov12_architecture()
        yolov12_hd.attr(dpi='300')
        # 保存为文件
        yolov12_hd.render('yolov12_architecture_hd', format='png', cleanup=True)
        st.success("高清架构图生成完成！")
        
        # 提供下载链接
        with open("yolov12_architecture_hd.png", "rb") as file:
            btn = st.download_button(
                label="下载高清架构图",
                data=file,
                file_name="yolov12_architecture_hd.png",
                mime="image/png"
            ) 