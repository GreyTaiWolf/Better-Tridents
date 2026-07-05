# 更好的三叉戟 / Better Tridents

一个面向 Minecraft 1.21.1 + NeoForge 的三叉戟玩法模组。当前核心玩法是“附灵”：带有附灵附魔的三叉戟投出后会成为围绕玩家行动的灵体三叉戟，能够自动攻击、蓄力锁定和回收。

## 版本信息

- Minecraft：`1.21.1`
- NeoForge：`21.1.234`
- Java：`21`
- Mod ID：`better_trident`
- 当前版本：`1.0.0`

## 功能概览

- 附灵三叉戟投出后会转换为灵体三叉戟，并在玩家周围悬浮。
- 悬浮状态下，三叉戟会围绕玩家移动，并响应玩家攻击的目标。
- 自动攻击采用惯性回转轨迹：命中后继续飞出，再像飞机/车辆转弯一样回击。
- 空主手长按左键可以蓄力发射，支持 1/2/3 级，最大距离分别约 100/200/300 格。
- 蓄力达到 1 级后，准星瞄准的可攻击生物会获得原版发光效果，松开发射会优先攻击高亮目标。
- 空主手右键准星指向悬浮三叉戟本体，可以收回三叉戟。
- 可攻击目标为非玩家 `LivingEntity`，包括怪物、动物、村民等；不会主动攻击玩家或主人。

## 基础玩法

1. 给三叉戟添加附灵附魔。
2. 投出三叉戟后，它会进入附灵逻辑。
3. 三叉戟悬浮在玩家附近时：
   - 攻击一个非玩家生物，三叉戟会自动追击。
   - 空主手长按左键，进入蓄力发射。
   - 空主手右键准星指向三叉戟本体，收回三叉戟。

更完整的操作说明见 [附灵三叉戟使用规范](docs/SPIRIT_TRIDENT_USAGE.md)。

## 开发命令

```powershell
.\gradlew.bat compileJava
.\gradlew.bat runClient
.\gradlew.bat runServer
```

## 项目结构

- `src/main/java/com/chena/bettertrident/entity/SpiritTridentEntity.java`：附灵三叉戟核心状态、移动、攻击、蓄力逻辑。
- `src/main/java/com/chena/bettertrident/client/ClientGameEvents.java`：客户端输入、召回射线、蓄力锁定目标上报。
- `src/main/java/com/chena/bettertrident/network/`：客户端到服务端的交互包。
- `src/main/resources/data/better_trident/enchantment/`：附灵附魔数据。
- `docs/`：玩法和开发文档。

## 文档

- [附灵三叉戟使用规范](docs/SPIRIT_TRIDENT_USAGE.md)
- [开发说明](docs/DEVELOPMENT.md)
- [更新日志](CHANGELOG.md)

