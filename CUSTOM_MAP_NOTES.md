# CustomMapを作る上での注意事項

## 基本の仕組み

Minecraftの「地図（MapItem）」は内部的に `MapView` オブジェクトで管理されていて、
`MapView` に複数の `MapRenderer` を追加することで描画を拡張できます。

- **既存の地図データ** → `MapView` に既に含まれている `MapRenderer` が描画している
- **オーバーレイを追加する** → 独自の `MapRenderer` を追加して、既存の地図の上に追加で描画

## ✅ 実装のポイント

### 1️⃣ MapView を取得

```java
ItemStack mapItem = ...; // プレイヤーの持っている地図
if (mapItem.getType() == Material.FILLED_MAP) {
    MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
    MapView mapView = Bukkit.getMap(mapMeta.getMapView().getId());
    // ここで MapView が取得できる
}
```

### 2️⃣ MapRenderer を実装

既存のレンダラーの後に自分の描画をすることで「オーバーレイ」になります。

```java
public class CustomOverlayRenderer extends MapRenderer {
    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        // 例えば赤い四角を描画する
        for (int x = 50; x < 60; x++) {
            for (int y = 50; y < 60; y++) {
                canvas.setPixel(x, y, MapPalette.RED);
            }
        }
    }
}
```

### 3️⃣ MapRenderer を MapView に追加

```java
mapView.addRenderer(new CustomOverlayRenderer());
```

- 既存の描画も残したい場合は **既存の MapRenderer を残したまま** 新しい `MapRenderer` を追加します。
- 既存の内容を消す必要がないなら `mapView.getRenderers().clear()` は呼ばないでください。

## ✅ 注意点

1. **MapRenderer の render() は毎 tick 呼ばれる可能性がある**ので、重い処理は避ける。

2. 他のプラグインや Vanilla の描画と競合しないように、**オーバーレイ描画だけ**にする。

3. 複数のプレイヤーが同じ地図を見る場合、**オーバーレイが全員に同じように表示されます**。

## ✅ 例：マーカーの座標を変える

例えば動的にマーカーを表示したい場合は、
`CustomOverlayRenderer` に現在の座標データを持たせておいて、
`render()` のたびに反映する形にします。

## ✨ まとめ

- `MapView` + `MapRenderer` を使えば地図に自由にオーバーレイを描画できる！
- 既存の地図データの上に重ねられるので、マーカーやオブジェクトも OK！
- 描画負荷には注意！

---

## Kotlinでの実装例

```kotlin
// MapViewを取得
val mapItem: ItemStack = player.inventory.itemInMainHand
if (mapItem.type == Material.FILLED_MAP) {
    val mapMeta = mapItem.itemMeta as MapMeta
    val mapView = Bukkit.getMap(mapMeta.mapView?.id ?: return)
    
    // カスタムレンダラーを追加
    mapView?.addRenderer(object : MapRenderer() {
        override fun render(map: MapView, canvas: MapCanvas, player: Player) {
            // オーバーレイ描画
            for (x in 50..59) {
                for (y in 50..59) {
                    canvas.setPixel(x, y, MapPalette.RED)
                }
            }
        }
    })
}
```

## パフォーマンス最適化のヒント

1. **描画フラグを使用**
```kotlin
class OptimizedRenderer : MapRenderer() {
    private var needsRedraw = true
    
    override fun render(map: MapView, canvas: MapCanvas, player: Player) {
        if (!needsRedraw) return
        
        // 描画処理
        // ...
        
        needsRedraw = false
    }
    
    fun updateData() {
        needsRedraw = true
    }
}
```

2. **時間ベースの更新**
```kotlin
class TimedRenderer : MapRenderer() {
    private var lastUpdate = 0L
    private val updateInterval = 1000L // 1秒ごと
    
    override fun render(map: MapView, canvas: MapCanvas, player: Player) {
        val now = System.currentTimeMillis()
        if (now - lastUpdate < updateInterval) return
        
        // 描画処理
        // ...
        
        lastUpdate = now
    }
}
```

## StormTrackerでの失敗から学んだこと

1. **地図の座標系は特殊** - ワールド座標から地図座標への変換に注意
2. **地形データの取得は困難** - デフォルトのMapRendererが既に地形を描画している場合のみ有効
3. **プレイヤーごとの表示制御は不可** - 全プレイヤーに同じ表示になる
4. **更新頻度に注意** - render()が頻繁に呼ばれるため、重い処理は避ける