package mvp2.utils

case class Settings(port: Int,
                    otherNodes: List[Node],
                    heartbeat: Int,
                    plannerHeartbeat: Int,
                    blockPeriod: Long,
                    biasForBlockPeriod: Long,
                    newBlockchain: Boolean,
                    apiSettings: ApiSettings,
                    ntp: NetworkTimeProviderSettings,
                    influx: InfluxSettings,
                    testingSettings: TestingSettings,
                    mempoolSetting: MempoolSetting
                    postgres: Option[PostgresSettings]
                   )

case class Node(host: String, port: Int)

case class ApiSettings(httpHost: String, httpPort: Int, timeout: Int)

case class InfluxSettings(host: String, port: Int, login: String, password: String)

case class NetworkTimeProviderSettings(server: String, updateEvery: Int, timeout: Int)

case class MempoolSetting(transactionsValidTime: Long, mempoolCleaningTime: Long)

case class TestingSettings(messagesTime: Boolean, iteratorsSyncTime: Int)

case class PostgresSettings(host: String, pass: String, user: String, read: Boolean, write: Boolean, batchSize: Option[Int])