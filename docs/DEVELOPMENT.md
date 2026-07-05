# 开发说明

## 环境要求

- JDK 21
- Minecraft 1.21.1
- NeoForge 21.1.234
- Gradle Wrapper：使用仓库内的 `gradlew.bat`

## 常用命令

```powershell
.\gradlew.bat compileJava
.\gradlew.bat runClient
.\gradlew.bat runServer
```

`compileJava` 用于快速验证 Java 源码是否能编译。`runClient` 用于启动开发客户端并实测手感。

## 代码约定

- 服务端负责真实状态、伤害、命中检测、目标校验和物品回收。
- 客户端只负责输入检测、准星射线、视觉预测和发送受校验的数据包。
- 附灵三叉戟所有可攻击目标应通过统一规则判断：存活的非玩家 `LivingEntity`，且不是主人。
- 自定义移动优先使用 `setDeltaMovement + move(MoverType.SELF, motion)`，避免频繁直接 `setPos` 造成视觉跳变。
- 新增状态字段时，需要同步考虑 NBT 保存/读取。

## 手动测试重点

- 悬浮移动是否平滑，重进世界后是否仍稳定。
- 空手右键准星指向悬浮三叉戟是否能收回。
- 空手右键空气、方块、其他实体是否不会误收回。
- 长按左键蓄力时，1/2/3 级音效、粒子和距离是否正常。
- 蓄力 1 级后，准星锁定目标是否发光；切换准星后高亮是否切换。
- 有高亮目标时松开发射，是否优先命中该目标。
- 攻击第一只生物后再攻击第二只生物，自动攻击是否继续响应。

## 发布前检查

```powershell
.\gradlew.bat compileJava
git status --short
```

确认编译通过，并检查提交内容是否只包含本次需要同步的文件。
