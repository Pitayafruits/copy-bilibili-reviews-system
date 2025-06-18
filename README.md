# 仿B站评论系统

本仓库是我的文章-《跟着大厂学架构01：如何利用开源方案，复刻B站那套“永不崩溃”的评论系统？》的代码实现。
文章链接：https://juejin.cn/post/7515695400671674419

## 🚀 项目特色

- **分布式架构**: 基于TiDB + Redis + Kafka的分布式架构设计
- **高并发处理**: 使用乐观锁处理点赞并发问题
- **实时数据同步**: 通过TiCDC + Kafka实现数据变更实时同步
- **缓存优化**: Redis缓存热门评论，提升查询性能
- **定时任务**: XXL-Job定时同步热门评论数据

## 📋 系统架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Spring Boot   │    │      TiDB       │    │      Redis      │
│   Application   │◄──►│    Cluster      │    │     Cache       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       ▲
         │                       │                       │
         ▼                       ▼                       │
┌─────────────────┐    ┌─────────────────┐              │
│     Kafka       │◄───│     TiCDC       │              │
│   Message       │    │  Change Data    │              │
│    Queue        │    │    Capture      │──────────────┘
└─────────────────┘    └─────────────────┘
         │
         ▼
┌─────────────────┐
│    XXL-Job      │
│   Scheduler     │
└─────────────────┘
```

## 🚀 快速开始

### 环境要求
- Java 17+
- Maven 3.6+
- Docker & Docker Compose

### 1. 克隆项目
```bash
git clone <repository-url>
cd copy-bilibili-reviews-system
```

### 2. 启动基础服务
```bash
# 启动TiDB集群、Kafka、Redis等服务
docker-compose up -d
```

### 3. 等待服务启动
```bash
# 检查服务状态
docker-compose ps

# 等待所有服务健康后继续
```

### 4. 启动应用
```bash
# 编译并启动Spring Boot应用
mvn clean spring-boot:run
```


## 🗂 项目结构

```
src/main/java/com/cc/
├── CopyBilibiliReviewsSystemApplication.java  # 启动类
├── config/                                    # 配置类
│   ├── RedisConfig.java                      # Redis配置
│   └── XxlJobConfig.java                     # XXL-Job配置
├── controller/                               # 控制器
│   └── CommentController.java                # 评论API
├── dto/                                      # 数据传输对象
│   └── CreateCommentRequest.java             # 创建评论请求
├── entity/                                   # 实体类
│   └── Comment.java                          # 评论实体
├── repository/                               # 数据访问层
│   └── CommentRepository.java                # 评论仓库
├── service/                                  # 服务层
│   ├── CommentService.java                   # 评论服务
│   └── CommentSyncService.java               # 同步服务
├── consumer/                                 # 消息消费者
│   └── CommentSyncConsumer.java              # 评论同步消费者
└── jobhandler/                               # 任务处理器
    └── CommentSyncXxlJobHandler.java         # 同步任务处理器
```

---
⭐ 如果这个项目对你有帮助，请给它一个星标！