import streamlit as st
import graphviz

# 设置页面配置
st.set_page_config(page_title="基于YOLOv12的交通流量监控和信号灯管理系统", layout="wide")

# 页面标题
st.title("基于YOLOv12的交通流量监控和信号灯管理系统结构图")
st.markdown("本应用展示了系统各模块的结构和关系")

# 创建选项卡
tabs = st.tabs(["系统总体架构", "前端架构", "后端架构", "Python模型服务", "核心功能模块", "数据流图", "部署架构"])

# 系统总体架构图
with tabs[0]:
    st.header("系统总体架构图")
    
    graph = graphviz.Digraph()
    graph.attr('node', shape='box', style='filled', fillcolor='lightblue', fontname='Microsoft YaHei')
    graph.attr('edge', fontname='Microsoft YaHei')
    
    with graph.subgraph(name='cluster_0') as c:
        c.attr(label='系统整体架构', fontname='Microsoft YaHei')
        c.node('Client', '客户端')
        c.node('Frontend', '前端应用')
        c.node('Backend', 'Java后端服务')
        c.node('PythonAPI', 'Python模型API')
        c.node('Model', 'YOLOv12模型', shape='box', style='filled', fillcolor='yellow')
        c.node('Database', '数据库', shape='cylinder', style='filled', fillcolor='lightgreen')
        c.node('FileStorage', '文件存储', shape='cylinder', style='filled', fillcolor='lightgreen')
        
        c.edge('Client', 'Frontend', label='HTTP/WebSocket')
        c.edge('Frontend', 'Backend', label='API调用')
        c.edge('Frontend', 'PythonAPI', label='直接API调用')
        c.edge('Backend', 'PythonAPI', label='内部服务调用')
        c.edge('Backend', 'Database', label='数据存储/读取')
        c.edge('PythonAPI', 'Model', label='加载模型')
        c.edge('PythonAPI', 'FileStorage', label='数据存储')
        c.edge('Backend', 'FileStorage', label='文件访问')
    
    st.graphviz_chart(graph)
    
    st.markdown("""
    ### 系统总体架构说明
    
    1. **客户端**：用户通过浏览器访问系统
    2. **前端应用**：基于Vue.js的Web应用，提供用户界面
    3. **Java后端服务**：处理业务逻辑、权限控制和数据管理
    4. **Python模型API**：提供机器学习模型服务，处理视频和图像分析
    5. **YOLOv12模型**：目标检测模型，用于车辆检测和识别
    6. **数据库**：存储用户数据、分析结果和系统配置
    7. **文件存储**：存储视频文件和分析产生的媒体文件
    """)

# 前端架构图
with tabs[1]:
    st.header("前端系统模块图")
    
    graph = graphviz.Digraph()
    graph.attr('node', shape='box', style='filled', fillcolor='lightblue', fontname='Microsoft YaHei')
    graph.attr('edge', fontname='Microsoft YaHei')
    
    with graph.subgraph(name='cluster_1') as c:
        c.attr(label='前端系统架构', fontname='Microsoft YaHei')
        c.node('App', 'App.vue', shape='box', style='filled', fillcolor='lightgreen')
        c.node('Router', '路由管理')
        c.node('Views', '视图组件')
        c.node('Components', '公共组件')
        c.node('ApiServices', 'API服务')
        c.node('Store', 'Vuex存储')
        c.node('Backend', '后端API', shape='box', style='filled', fillcolor='yellow')
        
        c.edge('App', 'Router')
        c.edge('Router', 'Views', label='路由')
        c.edge('Views', 'Components', label='使用')
        c.edge('Views', 'ApiServices', label='调用')
        c.edge('Views', 'Store', label='状态管理')
        c.edge('ApiServices', 'Backend', label='HTTP请求')
        
        with c.subgraph(name='cluster_1_1') as v:
            v.attr(label='视图模块', fontname='Microsoft YaHei')
            v.node('Home', '首页')
            v.node('Login', '登录/注册')
            v.node('VideoUpload', '视频上传')
            v.node('VideoResult', '视频分析结果')
            v.node('VideoHistory', '历史记录')
            v.node('UserManagement', '用户管理')
            v.node('PersonalInfo', '个人信息')
            v.node('Settings', '系统设置')
            
            v.edge('Views', 'Home')
            v.edge('Views', 'Login')
            v.edge('Views', 'VideoUpload')
            v.edge('Views', 'VideoResult')
            v.edge('Views', 'VideoHistory')
            v.edge('Views', 'UserManagement')
            v.edge('Views', 'PersonalInfo')
            v.edge('Views', 'Settings')
        
        with c.subgraph(name='cluster_1_2') as comp:
            comp.attr(label='组件模块', fontname='Microsoft YaHei')
            comp.node('CommonComp', '通用组件')
            comp.node('VideoComp', '视频相关组件')
            comp.node('AnalysisComp', '分析组件')
            comp.node('HistoryComp', '历史记录组件')
            
            comp.edge('Components', 'CommonComp')
            comp.edge('Components', 'VideoComp')
            comp.edge('Components', 'AnalysisComp')
            comp.edge('Components', 'HistoryComp')
    
    st.graphviz_chart(graph)
    
    st.markdown("""
    ### 前端架构说明
    
    1. **路由管理**：负责前端页面路由，基于Vue Router
    2. **视图组件**：各个页面的视图实现，如视频上传、分析结果等
    3. **公共组件**：可复用的UI组件，提高开发效率
    4. **API服务**：封装与后端交互的API调用
    5. **Vuex存储**：管理应用状态，实现组件间数据共享
    
    前端采用Vue.js框架，通过组件化和模块化设计提高代码复用率和维护性。
    """)

# 后端架构图
with tabs[2]:
    st.header("后端系统模块图")
    
    graph = graphviz.Digraph()
    graph.attr('node', shape='box', style='filled', fillcolor='lightblue', fontname='Microsoft YaHei')
    graph.attr('edge', fontname='Microsoft YaHei')
    
    with graph.subgraph(name='cluster_2') as c:
        c.attr(label='后端系统架构', fontname='Microsoft YaHei')
        c.node('SpringBoot', 'Spring Boot应用', shape='box', style='filled', fillcolor='lightgreen')
        c.node('Controllers', '控制器层')
        c.node('Services', '服务层')
        c.node('Repository', '数据持久层')
        c.node('Entities', '实体对象')
        c.node('WebSocket', 'WebSocket控制器')
        c.node('PythonServices', 'Python模型服务', shape='box', style='filled', fillcolor='yellow')
        
        c.edge('SpringBoot', 'Controllers', label='REST控制器')
        c.edge('Controllers', 'Services', label='业务逻辑')
        c.edge('Services', 'Repository', label='数据访问')
        c.edge('Services', 'PythonServices', label='调用')
        c.edge('Repository', 'Entities', label='ORM映射')
        c.edge('Controllers', 'WebSocket')
        
        with c.subgraph(name='cluster_2_1') as ctrl:
            ctrl.attr(label='控制器层', fontname='Microsoft YaHei')
            ctrl.node('AuthController', '身份认证')
            ctrl.node('AnalysisController', '分析控制')
            ctrl.node('VideoAnalysisController', '视频分析')
            ctrl.node('HistoryController', '历史记录')
            ctrl.node('UserController', '用户管理')
            ctrl.node('MediaController', '媒体管理')
            ctrl.node('ApiProxyController', 'API代理')
            ctrl.node('TrafficController', '交通管理')
            ctrl.node('SystemController', '系统管理')
            
            ctrl.edge('Controllers', 'AuthController')
            ctrl.edge('Controllers', 'AnalysisController')
            ctrl.edge('Controllers', 'VideoAnalysisController')
            ctrl.edge('Controllers', 'HistoryController')
            ctrl.edge('Controllers', 'UserController')
            ctrl.edge('Controllers', 'MediaController')
            ctrl.edge('Controllers', 'ApiProxyController')
            ctrl.edge('Controllers', 'TrafficController')
            ctrl.edge('Controllers', 'SystemController')
        
        with c.subgraph(name='cluster_2_2') as serv:
            serv.attr(label='服务层', fontname='Microsoft YaHei')
            serv.node('UserService', '用户服务')
            serv.node('AnalysisService', '分析服务')
            serv.node('VideoService', '视频服务')
            serv.node('StorageService', '存储服务')
            serv.node('SecurityService', '安全服务')
            serv.node('TrafficService', '交通服务')
            
            serv.edge('Services', 'UserService')
            serv.edge('Services', 'AnalysisService')
            serv.edge('Services', 'VideoService')
            serv.edge('Services', 'StorageService')
            serv.edge('Services', 'SecurityService')
            serv.edge('Services', 'TrafficService')
    
    st.graphviz_chart(graph)
    
    st.markdown("""
    ### 后端架构说明
    
    1. **Spring Boot应用**：基于Spring Boot框架的Java后端应用
    2. **控制器层**：处理HTTP请求，提供REST API接口
    3. **服务层**：实现核心业务逻辑，如视频分析、用户管理等
    4. **数据持久层**：负责数据库交互，使用JPA/Hibernate
    5. **WebSocket控制器**：处理实时通信，如分析进度推送
    6. **Python模型服务**：与Python模型API交互，调用机器学习模型
    
    后端采用经典的多层架构设计，实现关注点分离，提高代码的可维护性和可扩展性。
    """)

# Python模型服务图
with tabs[3]:
    st.header("Python模型服务架构")
    
    graph = graphviz.Digraph()
    graph.attr('node', shape='box', style='filled', fillcolor='lightblue', fontname='Microsoft YaHei')
    graph.attr('edge', fontname='Microsoft YaHei')
    
    with graph.subgraph(name='cluster_3') as c:
        c.attr(label='Python模型服务', fontname='Microsoft YaHei')
        c.node('ModelAPI', '模型API服务', shape='box', style='filled', fillcolor='lightgreen')
        c.node('ApiEndpoints', 'API端点')
        c.node('Controllers', 'API控制器')
        c.node('ModelServices', '模型服务')
        c.node('YOLOv12', 'YOLOv12模型', shape='box', style='filled', fillcolor='yellow')
        c.node('MongoDB', 'MongoDB', shape='cylinder', style='filled', fillcolor='lightgreen')
        c.node('GridFS', 'GridFS存储', shape='cylinder', style='filled', fillcolor='lightgreen')
        
        c.edge('ModelAPI', 'ApiEndpoints', label='Flask路由')
        c.edge('ApiEndpoints', 'Controllers', label='处理请求')
        c.edge('Controllers', 'ModelServices', label='业务逻辑')
        c.edge('ModelServices', 'YOLOv12', label='加载模型')
        c.edge('ModelServices', 'MongoDB', label='存储结果')
        c.edge('ModelServices', 'GridFS', label='文件存储')
        
        with c.subgraph(name='cluster_3_1') as api:
            api.attr(label='API端点', fontname='Microsoft YaHei')
            api.node('AnalyzeImage', '图像分析')
            api.node('AnalyzeVideo', '视频分析')
            api.node('MediaAccess', '媒体访问')
            api.node('StatusCheck', '状态检查')
            api.node('HistoryManage', '历史管理')
            
            api.edge('ApiEndpoints', 'AnalyzeImage')
            api.edge('ApiEndpoints', 'AnalyzeVideo')
            api.edge('ApiEndpoints', 'MediaAccess')
            api.edge('ApiEndpoints', 'StatusCheck')
            api.edge('ApiEndpoints', 'HistoryManage')
        
        with c.subgraph(name='cluster_3_2') as ms:
            ms.attr(label='模型服务功能', fontname='Microsoft YaHei')
            ms.node('VehicleDetection', '车辆检测')
            ms.node('TrafficAnalysis', '交通流量分析')
            ms.node('SignalOptimize', '信号灯优化')
            ms.node('VideoProcessing', '视频处理')
            ms.node('ReportGeneration', '报告生成')
            
            ms.edge('ModelServices', 'VehicleDetection')
            ms.edge('ModelServices', 'TrafficAnalysis')
            ms.edge('ModelServices', 'SignalOptimize')
            ms.edge('ModelServices', 'VideoProcessing')
            ms.edge('ModelServices', 'ReportGeneration')
    
    st.graphviz_chart(graph)
    
    st.markdown("""
    ### Python模型服务说明
    
    1. **模型API服务**：基于Flask的RESTful API服务
    2. **API端点**：提供图像/视频分析、状态查询等接口
    3. **模型服务**：封装YOLOv12模型的调用逻辑
    4. **车辆检测**：使用YOLOv12进行车辆目标检测
    5. **交通流量分析**：基于检测结果计算交通流量和密度
    6. **信号灯优化**：根据交通流量数据优化信号灯配时
    
    Python模型服务采用轻量级的Flask框架，与YOLOv12模型集成，提供高性能的视频分析能力。
    """)

# 核心功能模块图
with tabs[4]:
    st.header("核心功能模块关系图")
    
    graph = graphviz.Digraph()
    graph.attr('node', shape='box', style='filled', fillcolor='lightblue', fontname='Microsoft YaHei')
    graph.attr('edge', fontname='Microsoft YaHei')
    
    with graph.subgraph(name='cluster_4') as c:
        c.attr(label='核心功能模块', fontname='Microsoft YaHei')
        c.node('UserInterface', '用户界面', shape='box', style='filled', fillcolor='lightgreen')
        c.node('VideoManagement', '视频管理模块')
        c.node('VehicleDetection', '车辆检测模块', shape='box', style='filled', fillcolor='yellow')
        c.node('TrafficAnalysis', '交通流量分析模块')
        c.node('SignalControl', '信号灯控制模块')
        c.node('ReportGeneration', '报告生成模块')
        c.node('DataVisualization', '数据可视化模块')
        c.node('UserManagement', '用户管理模块')
        c.node('SecurityModule', '安全认证模块')
        c.node('SystemSettings', '系统设置模块')
        c.node('HistoryManagement', '历史记录模块')
        
        c.edge('UserInterface', 'VideoManagement')
        c.edge('VideoManagement', 'VehicleDetection')
        c.edge('VehicleDetection', 'TrafficAnalysis')
        c.edge('TrafficAnalysis', 'SignalControl')
        c.edge('TrafficAnalysis', 'ReportGeneration')
        c.edge('VehicleDetection', 'DataVisualization', label='检测结果')
        c.edge('TrafficAnalysis', 'DataVisualization', label='分析结果')
        c.edge('DataVisualization', 'UserInterface')
        
        c.edge('UserInterface', 'UserManagement')
        c.edge('UserManagement', 'SecurityModule')
        c.edge('UserInterface', 'SystemSettings')
        c.edge('UserInterface', 'HistoryManagement')
    
    st.graphviz_chart(graph)
    
    st.markdown("""
    ### 核心功能模块说明
    
    1. **用户界面**：用户交互的前端界面
    2. **视频管理模块**：处理视频上传、存储和检索
    3. **车辆检测模块**：基于YOLOv12的车辆目标检测
    4. **交通流量分析模块**：分析车流量、车速和车辆密度
    5. **信号灯控制模块**：根据交通分析结果优化信号灯配时
    6. **报告生成模块**：生成交通分析报告和可视化图表
    7. **数据可视化模块**：将分析结果转化为直观的图表和视图
    
    各功能模块之间有明确的数据流转关系，形成完整的业务处理流程。
    """)

# 数据流图
with tabs[5]:
    st.header("数据流图")
    
    graph = graphviz.Digraph()
    graph.attr('node', shape='box', style='filled', fillcolor='lightblue', fontname='Microsoft YaHei')
    graph.attr('edge', fontname='Microsoft YaHei')
    graph.attr(rankdir='LR')
    
    with graph.subgraph(name='cluster_5') as c:
        c.attr(label='系统数据流', fontname='Microsoft YaHei')
        c.node('VideoInput', '视频输入', shape='box', style='filled', fillcolor='lightgrey')
        c.node('Storage', '存储系统', shape='cylinder', style='filled', fillcolor='lightgreen')
        c.node('ModelProcessing', '模型处理', shape='box', style='filled', fillcolor='yellow')
        c.node('Detection', '检测结果')
        c.node('Analysis', '分析结果')
        c.node('Database', '数据库', shape='cylinder', style='filled', fillcolor='lightgreen')
        c.node('TrafficReport', '交通报告')
        c.node('SignalTiming', '信号灯时序')
        c.node('WebUI', 'Web界面', shape='box', style='filled', fillcolor='lightgrey')
        c.node('TrafficSignal', '交通信号灯', shape='box', style='filled', fillcolor='orange')
        
        c.edge('VideoInput', 'Storage', label='上传')
        c.edge('Storage', 'ModelProcessing', label='读取')
        c.edge('ModelProcessing', 'Detection', label='车辆检测')
        c.edge('Detection', 'Analysis', label='统计分析')
        c.edge('Analysis', 'Database', label='存储')
        c.edge('Analysis', 'TrafficReport', label='生成')
        c.edge('Analysis', 'SignalTiming', label='优化')
        c.edge('Database', 'WebUI', label='查询')
        c.edge('TrafficReport', 'WebUI')
        c.edge('SignalTiming', 'TrafficSignal', label='控制指令')
    
    st.graphviz_chart(graph)
    
    st.markdown("""
    ### 数据流说明
    
    1. **视频输入**：用户上传的交通监控视频
    2. **存储系统**：存储原始视频文件
    3. **模型处理**：使用YOLOv12模型进行视频处理
    4. **检测结果**：车辆检测的原始数据
    5. **分析结果**：基于检测结果的交通流量统计和分析
    6. **数据库**：持久化存储分析结果
    7. **交通报告**：生成交通状况报告
    8. **信号灯时序**：优化的信号灯配时方案
    9. **交通信号灯**：接收控制指令的实际交通信号设备
    
    数据在系统中流转，从原始视频到最终的控制指令，形成完整的处理流程。
    """)

# 部署架构图
with tabs[6]:
    st.header("系统部署架构图")
    
    graph = graphviz.Digraph()
    graph.attr('node', shape='box', style='filled', fillcolor='lightblue', fontname='Microsoft YaHei')
    graph.attr('edge', fontname='Microsoft YaHei')
    
    with graph.subgraph(name='cluster_6') as c:
        c.attr(label='系统部署架构', fontname='Microsoft YaHei')
        c.node('Client', '客户端浏览器', shape='box', style='filled', fillcolor='lightgrey')
        c.node('WebServer', 'Web服务器', shape='box', style='filled', fillcolor='lightgreen')
        c.node('FrontendServer', '前端应用服务器')
        c.node('BackendServer', 'Java后端服务器')
        c.node('PythonServer', 'Python模型服务器', shape='box', style='filled', fillcolor='yellow')
        c.node('Database', 'MySQL/PostgreSQL', shape='cylinder', style='filled', fillcolor='lightgreen')
        c.node('MongoDB', 'MongoDB/GridFS', shape='cylinder', style='filled', fillcolor='lightgreen')
        c.node('ModelStorage', '模型存储', shape='cylinder', style='filled', fillcolor='orange')
        c.node('FileSystem', '文件系统', shape='cylinder', style='filled', fillcolor='lightgrey')
        
        c.edge('Client', 'WebServer', label='HTTPS')
        c.edge('WebServer', 'FrontendServer', label='反向代理')
        c.edge('WebServer', 'BackendServer', label='反向代理')
        c.edge('WebServer', 'PythonServer', label='反向代理')
        c.edge('BackendServer', 'Database', label='数据访问')
        c.edge('PythonServer', 'MongoDB', label='文件存储')
        c.edge('PythonServer', 'ModelStorage', label='模型加载')
        c.edge('BackendServer', 'FileSystem', label='文件访问')
    
    st.graphviz_chart(graph)
    
    st.markdown("""
    ### 部署架构说明
    
    1. **客户端浏览器**：用户通过Web浏览器访问系统
    2. **Web服务器**：如Nginx，负责静态资源服务和反向代理
    3. **前端应用服务器**：部署Vue.js前端应用
    4. **Java后端服务器**：部署Spring Boot应用
    5. **Python模型服务器**：部署Flask API服务和YOLOv12模型
    6. **MySQL/PostgreSQL**：关系型数据库，存储业务数据
    7. **MongoDB/GridFS**：存储视频文件和分析结果
    8. **模型存储**：存储YOLOv12模型文件
    9. **文件系统**：存储系统临时文件和日志
    
    系统采用分布式部署架构，各组件可以根据负载情况进行水平扩展。
    """)

# 添加底部说明
st.markdown("---")
st.markdown("### 系统技术栈")
st.markdown("""
- **前端**：Vue.js + Vuex + Vue Router + Element UI
- **后端**：Spring Boot + Spring Security + JPA/Hibernate
- **Python服务**：Flask + YOLOv12 + OpenCV + TensorFlow
- **数据库**：MySQL/PostgreSQL + MongoDB
- **部署**：Nginx + Docker + Docker Compose
""")

# 运行方式说明
st.sidebar.header("运行说明")
st.sidebar.info("""
使用以下命令运行此Streamlit应用:
```
streamlit run streamlit_system_structure.py
```
""")

# 系统模块导航
st.sidebar.header("模块导航")
module_select = st.sidebar.selectbox(
    "选择要查看的模块:",
    [
        "系统总体架构", 
        "前端架构", 
        "后端架构", 
        "Python模型服务", 
        "核心功能模块", 
        "数据流图", 
        "部署架构"
    ]
)

# 系统简介
st.sidebar.header("系统简介")
st.sidebar.markdown("""
基于YOLOv12的交通流量监控和信号灯管理系统是一个综合性的智能交通解决方案，
通过视频分析技术实现车辆检测、交通流量监控和信号灯优化控制，
提高道路通行效率，减少交通拥堵。
""") 