# Minecraft Legends - Battle Royaleプラグイン

## プロジェクト概要

Minecraft上でバトルロイヤルゲームを再現するPaperプラグインです。
Clean Architectureに基づいた設計で、拡張性と保守性を重視したシステムを構築します。

## 技術仕様

- **対象プラットフォーム**: Paper（最新版）
- **言語**: Kotlin
- **アーキテクチャ**: Clean Architecture
- **データベース**: SQLite（初期実装）
- **国際化**: YAML設定ファイル
- **必須プラグイン**: WorldBorder

## ゲーム仕様

### 基本ゲームシステム

#### プレイヤー構成
- **最大プレイヤー数**: 24名
- **チーム構成**: 3人1チーム（8チーム）
- **最小開始人数**: 12名
- **マップサイズ**: 3000x3000ブロック（設定可能）

#### 勝利条件
1. 敵チームを全て倒す（通常勝利）
2. リング完全収縮時に安全地帯内で生存（生存勝利）
3. 同時に複数人が残った場合はHPの多い方が勝利

### チーム編成システム

#### チーム作成
- 事前にチームを作成可能
- チームはゲーム終了後も継続（永続化）
- データベースに保存

#### 編成の優先順位
1. **最優先**: 3人フルチーム（事前に組まれたチーム）
2. **次に優先**: ソロ参加者（3人ずつランダムにグループ化）
3. **最後**: 不完全チーム（2人チーム、1人チーム）

#### チーム関連コマンド
```
/br team create [チーム名]     # チーム作成
/br team invite <プレイヤー>   # プレイヤーを招待
/br team join <チーム名>      # チームに参加
/br team leave               # チームから脱退
/br team disband             # チーム解散
/br team info                # チーム情報確認
```

### レジェンドシステム

#### 初期実装レジェンド

**1. 移動タイプ（パスファインダー風）**
- 固定アイテム: 釣竿
- アビリティ: グラップリング移動
- クールダウン: 設定可能

**2. アサシンタイプ（レイス風）**
- アビリティ: 数秒間透明化（攻撃不可）
- 透明時間: 設定可能
- クールダウン: 設定可能

**3. 防御タイプ（ライフライン風）**
- アビリティ: 自分と他人を回復
- 回復量: 設定可能
- クールダウン: 設定可能

#### レジェンド選択システム
- ゲーム開始前に選択フェーズ（60秒）
- チーム内で重複不可（先着順）
- 未選択者には残りから自動割り当て
- 拡張可能な設計（後からレジェンド追加可能）

### リング収縮システム

#### 収縮パターン（約40分ゲーム）
| ラウンド | 収縮開始まで | 収縮時間 | リング外ダメージ |
|---------|------------|---------|----------------|
| 1 | 3分 | 2分 | 2/秒 |
| 2 | 1分30秒 | 1分30秒 | 3/秒 |
| 3 | 1分30秒 | 1分30秒 | 5/秒 |
| 4 | 1分20秒 | 1分 | 10/秒 |
| 5 | 1分20秒 | 40秒 | 15/秒 |
| 6 | 1分 | 30秒 | 20/秒 |
| 7 | 30秒 | 2分 | 50/秒（最終収縮） |

#### 収縮仕様
- **収縮間隔**: 3秒ごとに段階的に縮小
- **中心座標**: 収縮時にランダムに変更
- **最終サイズ**: 0（完全消失）
- **実装**: WorldBorderプラグインを使用

### リスポーンシステム

#### リスポーンビーコン
- **構造**: 9マス（3x3）の鉄ブロック台座、中央にビーコン
- **ビーム**: 白色のビーコンビーム
- **配置数**: デフォルト6個（設定可能）
- **配置**: 3000x3000マップにランダム配置

#### 復活操作
- **操作方法**: ビーコンに設置された石のボタンを長押し
- **復活時間**: 7秒（設定可能）
- **進行表示**: ボスバーで進行状況表示
- **中断条件**: ボタンを離すとリセット

#### 復活の流れ
1. チームメイトがダウン状態になる
2. チームメイトのだれかの視点になり、これは切り替えできる
3. 死んだところに墓場のチェストができる。
4. 墓場をタッチするとプレイヤーのネームタグを入手できる。ただしこれは５分たつと入手できない
3. リスポーンビーコンに移動
4. 石のボタンを7秒間長押し
5. 復活完了（もっている仲間のタグ全員が復活）

### サプライボックスシステム

#### 配置仕様
- **形式**: チェスト形式
- **配置場所**: リスポーンビーコン周辺（半径20ブロック程度）
- **配置数**: ビーコン1つにつき3〜5個
- **内容**: 武器、防具、回復アイテム等

#### アイテム構成
- **武器系**: 剣、弓、クロスボウ、トライデント等
- **防具系**: 革〜ネザライト（レアリティ別）
- **回復系**: ポーション、食料、ゴールデンアップル等
- **特殊アイテム**: エンダーパール、ファイアーワーク等

### ワールド生成システム

#### マップ生成
- **サイズ**: 3000x3000（設定可能）
- **生成方法**: ゲーム開始時に新規ワールド作成
- **地形**: バニラ地形生成
- **構造物**: リスポーンビーコンとサプライボックスを自動配置

### 統計・称号システム

#### 記録する統計データ
- Kill（キル数）
- Death（デス数）
- Win（勝利数）
- Damage（与ダメージ）
- MaxKillsInGame（1ゲーム中の最大キル数）
- UsedLegendCount（使用したレジェンド数）
- LegendStats（レジェンド毎のKill、Death、Win、Damage）
- ReviveCount（味方を復活させた回数）

#### 拡張可能な設計
- **統計データ**: クラスベースで実装、後から追加可能
- **称号システム**: YAML設定ファイルで定義
- **データベース**: 柔軟なスキーマ設計

#### 称号システム
- **設定方法**: titles.yml で条件と称号を定義
- **条件**: 複数条件の組み合わせ（AND/OR）対応
- **表示**: 称号の表示優先度と色も設定可能

### 国際化システム

#### 多言語対応
- **設定ファイル**: messages_ja.yml, messages_en.yml 等
- **切り替え**: 設定でランゲージ切り替え可能
- **拡張性**: 新しい言語の追加が容易

#### メッセージ形式
```yaml
# messages_ja.yml
game:
  start: "ゲームが開始されました！"
  winner: "%team%がチャンピオンです！"
  ring_warning: "リングが%time%秒後に収縮します！"

# messages_en.yml  
game:
  start: "Game has started!"
  winner: "%team% is the champion!"
  ring_warning: "Ring will shrink in %time% seconds!"
```

### 外部API連携システム

#### 世界ランキング機能
- **目的**: 外部RESTサーバーと連携した世界ランキング
- **実装**: 後から追加実装可能な設計
- **フォールバック**: API未使用時はローカル統計のみ

#### API仕様（予定）
- **認証**: トークンベース認証
- **送信データ**: プレイヤー統計（Kill、Death、Win、Damage等）
- **取得データ**: 世界ランキング情報

### ゲームループシステム

#### ゲームの流れ
1. **待機ロビー**（チーム編成可能）
2. **ゲーム開始**（最小人数到達で自動開始）
3. **レジェンド選択**（60秒間）
4. **ゲーム実行**（バトルロイヤル）
5. **ゲーム終了**（勝者決定）
6. **結果表示**・統計更新
7. **待機時間**（60秒）
8. **次のゲーム開始**へ

#### 自動化設定
```yaml
game-loop:
  waiting-time: 60      # ゲーム間の待機時間
  min-players: 12       # 最小開始人数
  max-players: 24       # 最大プレイヤー数
  team-size: 3          # チームサイズ
  auto-start: true      # 自動開始有効
```

## アーキテクチャ設計

### Clean Architecture パッケージ構成

```
com.hacklab.minecraft_legends
├── domain/              # ビジネスロジック（エンティティ、ユースケース）
│   ├── entity/          # ゲーム、プレイヤー、チーム等のエンティティ
│   ├── usecase/         # ゲーム開始、統計更新等のユースケース
│   └── repository/      # データアクセス抽象化
├── infrastructure/     # 外部システム（データベース、ファイル）
│   ├── database/        # データベース実装
│   ├── config/          # 設定ファイル管理
│   └── world/           # ワールド操作
├── presentation/       # UI層（コマンド、イベント）
│   ├── command/         # コマンド処理
│   ├── listener/        # イベントリスナー
│   └── gui/             # GUI操作
└── application/        # アプリケーション層
    ├── service/         # アプリケーションサービス
    └── dto/             # データ転送オブジェクト
```

### 依存関係のルール
- **Domain層**: 他の層に依存しない
- **Application層**: Domain層のみに依存
- **Infrastructure層**: Domain層に依存（実装）
- **Presentation層**: Application層とDomain層に依存

### インターフェース設計例

#### Repository（データアクセス抽象化）
```kotlin
interface PlayerRepository {
    suspend fun findById(id: UUID): Player?
    suspend fun save(player: Player): Player
    suspend fun findByName(name: String): Player?
}

interface TeamRepository {
    suspend fun findById(id: UUID): Team?
    suspend fun save(team: Team): Team
    suspend fun findByPlayerId(playerId: UUID): Team?
}

interface StatisticsRepository {
    suspend fun getPlayerStats(playerId: UUID): PlayerStats
    suspend fun updatePlayerStats(stats: PlayerStats)
    suspend fun getLeaderboard(limit: Int): List<PlayerStats>
}
```

#### UseCase（ビジネスロジック）
```kotlin
interface StartGameUseCase {
    suspend fun execute(players: List<Player>): Result<Game>
}

interface ProcessKillUseCase {
    suspend fun execute(killer: Player, victim: Player): Result<Unit>
}

interface UpdateStatisticsUseCase {
    suspend fun execute(playerId: UUID, stats: StatisticsUpdate): Result<Unit>
}
```

#### Service（アプリケーションサービス）
```kotlin
interface MessageService {
    fun getMessage(key: String, vararg args: Any): String
    fun setLanguage(language: String)
}

interface RankingService {
    suspend fun submitPlayerStats(playerStats: PlayerStats): Result<Boolean>
    suspend fun getWorldRanking(): Result<List<RankingEntry>>
}
```

## 設定ファイル

### メイン設定（config.yml）
```yaml
# ワールド設定
world:
  size: 3000  # マップサイズ (3000x3000)
  
# リスポーンビーコン設定
respawn-beacon:
  count: 6  # ビーコンの数
  respawn-time: 7  # 復活にかかる秒数
  
# ゲーム設定
game:
  max-players: 24  # 最大プレイヤー数
  team-size: 3  # チームサイズ
  min-players: 12  # 最小開始人数
  legend-selection-time: 60  # レジェンド選択時間（秒）

# リング設定
ring:
  phases:
    1:
      wait-time: 180  # 3分
      shrink-time: 120  # 2分
      damage: 2
    2:
      wait-time: 90  # 1分30秒
      shrink-time: 90  # 1分30秒
      damage: 3
    # ... 他のフェーズ
    7:  # 最終フェーズ
      wait-time: 30
      shrink-time: 120
      damage: 50
      final-size: 0

# サプライボックス設定
supply-boxes:
  per-beacon: 4  # ビーコンあたりのチェスト数
  spawn-radius: 20  # ビーコンからの配置半径

# 外部API設定
api:
  enabled: false  # 初期は無効
  base-url: "https://api.example.com"
  token: ""
  timeout: 5000

# 国際化設定
language:
  default: "ja"
  available: ["ja", "en"]

# ゲームループ設定
game-loop:
  waiting-time: 60  # ゲーム間の待機時間
  auto-start: true  # 自動開始
```

### 称号設定（titles.yml）
```yaml
titles:
  - id: "champion"
    name: "&6チャンピオン"
    description: "10回勝利する"
    conditions:
      wins: 10
    priority: 1
    
  - id: "killer"
    name: "&4キラー"
    description: "100キルを達成する"
    conditions:
      kills: 100
    priority: 2
    
  - id: "survivor"
    name: "&2サバイバー"
    description: "50回復活させる"
    conditions:
      revive_count: 50
    priority: 3

  - id: "legend_master"
    name: "&5レジェンドマスター"
    description: "全てのレジェンドで勝利する"
    conditions:
      and:
        - used_legends: 3
        - wins: 1
    priority: 4
```

### 戦利品テーブル（loot-tables.yml）
```yaml
loot-tables:
  common:
    - material: IRON_SWORD
      chance: 30
      enchantments:
        - type: DAMAGE_ALL
          level: 1
          chance: 50
    - material: BOW
      chance: 25
    - material: BREAD
      chance: 20
      amount: 3-5
      
  rare:
    - material: DIAMOND_SWORD
      chance: 15
      enchantments:
        - type: DAMAGE_ALL
          level: 2
          chance: 80
    - material: GOLDEN_APPLE
      chance: 10
      amount: 1-2
      
  epic:
    - material: NETHERITE_SWORD
      chance: 5
      enchantments:
        - type: DAMAGE_ALL
          level: 3
          chance: 100
    - material: ENCHANTED_GOLDEN_APPLE
      chance: 3
```

## 開発ガイドライン

### コーディング規約
- **クラス名**: PascalCase（例：`GameManager`）
- **関数名**: camelCase（例：`startGame()`）
- **変数名**: camelCase（例：`playerCount`）
- **定数**: UPPER_SNAKE_CASE（例：`MAX_PLAYERS`）
- **パッケージ**: すべて小文字（例：`com.hacklab.minecraft_legends.domain.entity`）

### テスト指針
- 各レイヤーのユニットテスト
- ドメインロジックのテストを重視
- モックを使用したインフラ層のテスト

### 拡張性の考慮
- 新しいレジェンドの追加が容易
- 新しい統計データの追加が容易
- 新しい称号の追加が容易
- 外部APIとの連携が後から追加可能

## 今後の拡張予定

### フェーズ1（基本実装）
- [x] 基本仕様の策定
- [ ] コアシステムの実装
- [ ] 基本的なゲームループ
- [ ] 3つのレジェンド実装

### フェーズ2（機能拡張）
- [ ] 追加レジェンドの実装
- [ ] 高度な統計システム
- [ ] 称号システムの充実
- [ ] パフォーマンス最適化

### フェーズ3（外部連携）
- [ ] 外部API連携
- [ ] 世界ランキング機能
- [ ] Webダッシュボード
- [ ] 大会モード

## 参考情報

### 必要な権限
- ワールド作成・管理
- プレイヤーのテレポート
- エンティティ操作
- データベースアクセス

### パフォーマンス考慮事項
- リング収縮は3秒間隔で負荷軽減
- 統計更新は非同期処理
- ワールド生成は別スレッドで実行
- メモリ使用量の監視

### セキュリティ考慮事項
- プレイヤー権限の適切な制御
- 外部API通信の暗号化
- 統計データの整合性保証
- チート対策の実装

---

**最終更新**: 2025-07-01  
**作成者**: HackLab Team  
**バージョン**: 1.0.0-SNAPSHOT