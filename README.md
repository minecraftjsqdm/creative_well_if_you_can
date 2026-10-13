# Creative well if you can

NeoForge 1.21.1 · MIT · mod id `creativewell` · v0.1.0

## English

Rewrites the creative-mode inventory screen for speed and comfort.

- **Dynamic grid sizing** — the item grid resizes to fit the window instead of being fixed.
- **Scrollable tab list** on the left, so mod/pack tabs never run off screen.
- **Always-available search bar** with an optional per-tab or shared search text mode.
- **Settings** — set the column count (including unlimited), and optionally "Cram JEI too!" so JEI follows the same density.
- **Shift+click** behaviour — choose between "full stack to cursor" and "64 into backpack".
- **JEI search link** — off, one-way (JEI → here) or both ways.
- **Relics** button.

## 中文

重写创造模式物品栏界面，追求速度与顺手。

- 动态网格：物品网格随窗口自动伸缩，而非固定尺寸。
- 左侧可滚动标签列表，模组 / 整合包标签页不再溢出屏幕。
- 常驻搜索栏，搜索文本可选「按标签页」或「全局共享」。
- 设置项：列数设置（含无限列），并可勾选「连 JEI 一起塞满！」让 JEI 跟随相同密度。
- Shift+点击行为：可选「整叠移到光标」或「64 个进背包」。
- JEI 搜索联动：关闭 / 单向（JEI → 此处）/ 双向。
- **Relics** 按钮。

## Dependencies / 依赖

- Required / 必需：NeoForge 21.1.x on Minecraft 1.21.1
- Optional / 可选：`jei`（`>= 19.54.0.429`）、`curios`

## Build / 构建

```sh
# Windows
$env:JAVA_HOME='<path-to-jdk21>'; .\gradlew.bat build --console=plain -q

# Linux / macOS
JAVA_HOME=<path-to-jdk21> ./gradlew build
```

Output jar: `build/libs/creativewell-0.1.0.jar`. 产物：`build/libs/creativewell-0.1.0.jar`。

## License / 许可

MIT. See `LICENSE`.
