package com.hacklab.minecraft_legends.infrastructure.database

import com.hacklab.minecraft_legends.infrastructure.logger.LoggerFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

interface DatabaseManager {
    suspend fun initialize()
    suspend fun shutdown()
    suspend fun getConnection(): Connection
    suspend fun executeUpdate(sql: String, vararg params: Any): Int
    suspend fun <T> executeQuery(sql: String, vararg params: Any, mapper: (java.sql.ResultSet) -> T): List<T>
    suspend fun <T> executeQuerySingle(sql: String, vararg params: Any, mapper: (java.sql.ResultSet) -> T): T?
    suspend fun executeTransaction(block: suspend (Connection) -> Unit)
}

class SQLiteDatabaseManager(
    private val databaseFile: File,
    private val poolSize: Int = 10
) : DatabaseManager {
    
    private val logger = LoggerFactory.getLogger()
    private var dataSource: HikariDataSource? = null
    
    override suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            logger.info("Initializing SQLite database: ${databaseFile.absolutePath}")
            
            // データベースファイルのディレクトリを作成
            databaseFile.parentFile?.mkdirs()
            
            // HikariCP設定
            val config = HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:${databaseFile.absolutePath}"
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = poolSize
                minimumIdle = 1
                connectionTimeout = 30000
                idleTimeout = 600000
                maxLifetime = 1800000
                
                // SQLite固有の設定
                addDataSourceProperty("journal_mode", "WAL")
                addDataSourceProperty("synchronous", "NORMAL")
                addDataSourceProperty("foreign_keys", "ON")
                addDataSourceProperty("busy_timeout", "30000")
            }
            
            dataSource = HikariDataSource(config)
            
            // テーブル作成
            createTables()
            
            logger.info("Database initialized successfully")
            
        } catch (e: Exception) {
            logger.error("Failed to initialize database", e)
            throw e
        }
    }
    
    override suspend fun shutdown() = withContext(Dispatchers.IO) {
        try {
            logger.info("Shutting down database")
            dataSource?.close()
            dataSource = null
            logger.info("Database shutdown completed")
        } catch (e: Exception) {
            logger.error("Error during database shutdown", e)
        }
    }
    
    override suspend fun getConnection(): Connection = withContext(Dispatchers.IO) {
        dataSource?.connection ?: throw IllegalStateException("Database not initialized")
    }
    
    override suspend fun executeUpdate(sql: String, vararg params: Any): Int = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                stmt.executeUpdate()
            }
        }
    }
    
    override suspend fun <T> executeQuery(
        sql: String, 
        vararg params: Any, 
        mapper: (java.sql.ResultSet) -> T
    ): List<T> = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<T>()
                    while (rs.next()) {
                        results.add(mapper(rs))
                    }
                    results
                }
            }
        }
    }
    
    override suspend fun <T> executeQuerySingle(
        sql: String, 
        vararg params: Any, 
        mapper: (java.sql.ResultSet) -> T
    ): T? = withContext(Dispatchers.IO) {
        executeQuery(sql, *params, mapper = mapper).firstOrNull()
    }
    
    override suspend fun executeTransaction(block: suspend (Connection) -> Unit) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.autoCommit = false
            try {
                block(conn)
                conn.commit()
            } catch (e: Exception) {
                try {
                    conn.rollback()
                } catch (rollbackException: SQLException) {
                    logger.error("Failed to rollback transaction", rollbackException)
                }
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }
    
    private suspend fun createTables() = withContext(Dispatchers.IO) {
        logger.info("Creating database tables")
        
        val tableCreationSql = listOf(
            // Players table
            """
            CREATE TABLE IF NOT EXISTS players (
                id TEXT PRIMARY KEY,
                name TEXT UNIQUE NOT NULL,
                created_at TEXT NOT NULL,
                last_seen TEXT NOT NULL
            )
            """.trimIndent(),
            
            // Player statistics table
            """
            CREATE TABLE IF NOT EXISTS player_statistics (
                player_id TEXT PRIMARY KEY,
                kills INTEGER DEFAULT 0,
                deaths INTEGER DEFAULT 0,
                wins INTEGER DEFAULT 0,
                damage INTEGER DEFAULT 0,
                max_kills_in_game INTEGER DEFAULT 0,
                revive_count INTEGER DEFAULT 0,
                used_legends TEXT DEFAULT '[]',
                FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            
            // Legend statistics table
            """
            CREATE TABLE IF NOT EXISTS legend_statistics (
                player_id TEXT,
                legend_id TEXT,
                kills INTEGER DEFAULT 0,
                deaths INTEGER DEFAULT 0,
                wins INTEGER DEFAULT 0,
                damage INTEGER DEFAULT 0,
                time_played INTEGER DEFAULT 0,
                PRIMARY KEY (player_id, legend_id),
                FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            
            // Custom statistics table
            """
            CREATE TABLE IF NOT EXISTS custom_statistics (
                player_id TEXT,
                stat_key TEXT,
                stat_value TEXT,
                stat_type TEXT,
                PRIMARY KEY (player_id, stat_key),
                FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            
            // Player settings table
            """
            CREATE TABLE IF NOT EXISTS player_settings (
                player_id TEXT PRIMARY KEY,
                language TEXT DEFAULT 'ja',
                current_title TEXT,
                show_title_in_chat INTEGER DEFAULT 1,
                show_title_in_tab INTEGER DEFAULT 1,
                auto_join_game INTEGER DEFAULT 1,
                preferred_legend TEXT,
                show_damage_numbers INTEGER DEFAULT 1,
                show_kill_messages INTEGER DEFAULT 1,
                enable_sounds INTEGER DEFAULT 1,
                custom_settings TEXT DEFAULT '{}',
                FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            
            // Teams table
            """
            CREATE TABLE IF NOT EXISTS teams (
                id TEXT PRIMARY KEY,
                name TEXT UNIQUE NOT NULL,
                leader_id TEXT NOT NULL,
                max_size INTEGER DEFAULT 3,
                is_public INTEGER DEFAULT 1,
                created_at TEXT NOT NULL,
                last_active TEXT NOT NULL,
                FOREIGN KEY (leader_id) REFERENCES players(id)
            )
            """.trimIndent(),
            
            // Team members table
            """
            CREATE TABLE IF NOT EXISTS team_members (
                team_id TEXT,
                player_id TEXT,
                joined_at TEXT NOT NULL,
                PRIMARY KEY (team_id, player_id),
                FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
                FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            
            // Games table
            """
            CREATE TABLE IF NOT EXISTS games (
                id TEXT PRIMARY KEY,
                world_name TEXT NOT NULL,
                state TEXT NOT NULL,
                start_time TEXT,
                end_time TEXT,
                winner_team_id TEXT,
                current_ring_phase INTEGER DEFAULT 0,
                next_ring_time TEXT,
                settings TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
            
            // Game teams table
            """
            CREATE TABLE IF NOT EXISTS game_teams (
                game_id TEXT,
                team_id TEXT,
                name TEXT NOT NULL,
                kills INTEGER DEFAULT 0,
                placement INTEGER,
                eliminated_at TEXT,
                PRIMARY KEY (game_id, team_id),
                FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
                FOREIGN KEY (team_id) REFERENCES teams(id)
            )
            """.trimIndent(),
            
            // Game players table
            """
            CREATE TABLE IF NOT EXISTS game_players (
                game_id TEXT,
                player_id TEXT,
                team_id TEXT,
                name TEXT NOT NULL,
                legend TEXT NOT NULL,
                is_alive INTEGER DEFAULT 1,
                kills INTEGER DEFAULT 0,
                damage INTEGER DEFAULT 0,
                revives INTEGER DEFAULT 0,
                death_time TEXT,
                respawn_beacon_used INTEGER DEFAULT 0,
                PRIMARY KEY (game_id, player_id),
                FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
                FOREIGN KEY (player_id) REFERENCES players(id),
                FOREIGN KEY (team_id) REFERENCES teams(id)
            )
            """.trimIndent(),
            
            // Player titles table
            """
            CREATE TABLE IF NOT EXISTS player_titles (
                player_id TEXT,
                title_id TEXT,
                earned_at TEXT NOT NULL,
                is_equipped INTEGER DEFAULT 0,
                PRIMARY KEY (player_id, title_id),
                FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            
            // Legend selections table
            """
            CREATE TABLE IF NOT EXISTS legend_selections (
                game_id TEXT,
                team_id TEXT,
                player_id TEXT,
                selected_legend TEXT,
                is_locked INTEGER DEFAULT 0,
                selection_time TEXT NOT NULL,
                PRIMARY KEY (game_id, player_id),
                FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
                FOREIGN KEY (team_id) REFERENCES teams(id),
                FOREIGN KEY (player_id) REFERENCES players(id)
            )
            """.trimIndent(),
            
            // Ability cooldowns table
            """
            CREATE TABLE IF NOT EXISTS ability_cooldowns (
                player_id TEXT,
                legend_id TEXT,
                ability_type TEXT,
                cooldown_end_time TEXT,
                PRIMARY KEY (player_id, legend_id, ability_type),
                FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        
        tableCreationSql.forEach { sql ->
            try {
                executeUpdate(sql)
            } catch (e: Exception) {
                logger.error("Failed to create table: $sql", e)
                throw e
            }
        }
        
        logger.info("Database tables created successfully")
    }
}