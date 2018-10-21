package net.mmm.survival.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.PAS123.Group.Group.Group;
import net.mmm.survival.SurvivalData;
import net.mmm.survival.player.Complaint;
import net.mmm.survival.player.LevelPlayer;
import net.mmm.survival.player.SurvivalLicence;
import net.mmm.survival.player.SurvivalPlayer;
import net.mmm.survival.regions.SurvivalWorld;
import net.mmm.survival.util.ObjectBuilder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Verwaltung der MySQL-Datenbank-Verbindung
 */
public class AsyncMySQL {
  /**
   * Verbindungsdaten
   */
  private static final int PORT = 3306;
  private static final String DATABASE = "mcmysql_1_nick";
  private static final String HOST = "sql430.your-server.de";
  private static final String PASSWORD = "gxI9C2t8i2z6Sf7a";
  private static final String USER = "mcmysql_nick";

  private AsyncMySQL.MySQL sql;
  private ExecutorService executor;

  /**
   * Konstruktor
   */
  public AsyncMySQL() {
    try {
      sql = new AsyncMySQL.MySQL();
      executor = Executors.newCachedThreadPool();
    } catch (final ClassNotFoundException | SQLException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Spielername aus einer UUID
   *
   * @param uuid einzigartiger Identifier
   * @return Name des Spielers
   */
  public String getName(final UUID uuid) {
    return getMySQL().query("SELECT name FROM Playerstatus where UUID='" + uuid + "'");
  }

  /**
   * Vote hinzufuegen
   *
   * @param uuid einzigartiger Identifier
   * @param website Webseite
   */
  public void addVote(final UUID uuid, final String website) {
    sql.update("INSERT INTO Votes (UUID, Time, Website) VALUES (" + uuid + ", " +
        System.currentTimeMillis() + ", " + website + ")");
  }

  /**
   * Playerliste abfragen
   *
   * @return Playerliste
   */
  public Map<UUID, SurvivalPlayer> getPlayers() {
    final Map<UUID, SurvivalPlayer> survivalPlayers = new HashMap<>();

    final Connection connection = getMySQL().connection;
    try (final Statement statement = connection.createStatement();
         final ResultSet resultSet = statement
             .executeQuery("SELECT UUID, MONEY, LICENCES, VOTES, MAXZONE, HOME, LEVELPLAYER FROM SurvivalPlayer")) {
      while (resultSet.next()) {
        final UUID uuid = UUID.fromString(resultSet.getString(1));
        survivalPlayers.put(uuid, determinePlayer(resultSet, uuid));
      }
    } catch (final SQLException ex) {
      ex.printStackTrace();
    }
    return survivalPlayers;
  }

  private SurvivalPlayer determinePlayer(final ResultSet resultSet, final UUID uuid) throws SQLException {
    final double money = resultSet.getDouble(2);

    final List<Complaint> complaints = determineComplaints(uuid);

    final List<SurvivalLicence> licences = determineLicences(resultSet.getString(3));

    final short votes = (short) resultSet.getInt(4);

    final int maxzone = resultSet.getInt(5);

    final Location location = determineLocation(resultSet.getString(6));

    LevelPlayer levelPlayer = new LevelPlayer();
    if (resultSet.getString(7) != null) {
      levelPlayer = (LevelPlayer) ObjectBuilder.getObjectOf(resultSet.getString(7));
    }

    return new SurvivalPlayer(uuid, money, complaints, licences, votes, maxzone, location, levelPlayer);
  }

  private List<Complaint> determineComplaints(final UUID uuid) {
    final ArrayList<Complaint> complaints = new ArrayList<>();

    try (final Statement statement = getMySQL().connection.createStatement();
         final ResultSet resultSet = statement
             .executeQuery("SELECT UUID, ID, REASON, OPERATOR, DATE FROM SurvivalPlayerComplaints")) {
      while (resultSet.next()) {
        final UUID uuidComplaint = UUID.fromString(resultSet.getString(1));
        final int id = resultSet.getInt(2);
        final String reason = resultSet.getString(3);
        final UUID operator = UUID.fromString(resultSet.getString(4));
        final java.util.Date date = resultSet.getTimestamp(5);
        if (uuidComplaint.equals(uuid)) {
          complaints.add(new Complaint(uuid, id, reason, operator, date));
        }
      }
    } catch (final SQLException ex) {
      ex.printStackTrace();
    }

    return complaints;
  }

  private List<SurvivalLicence> determineLicences(final String licencesString) {
    final List<SurvivalLicence> licences = new ArrayList<>();
    if (licencesString != null && !licencesString.isEmpty()) {
      for (final String licence : licencesString.split(",")) {
        licences.add(SurvivalLicence.valueOf(licence));
      }
    }

    return licences;
  }

  private Location determineLocation(final String homeString) {
    final World bauWorld = SurvivalWorld.BAUWELT.get();
    final double x = Double.parseDouble(homeString.split("/")[0]);
    final double y = Double.parseDouble(homeString.split("/")[1]);
    final double z = Double.parseDouble(homeString.split("/")[2]);
    return new Location(bauWorld, x, y, z);
  }

  /**
   * Liste mit allen Spieler auf diesem Server abfragen
   *
   * @return Playerliste
   */
  public Map<UUID, String> getPlayerCache() {
    final Map<UUID, String> cache = new HashMap<>();
    final Connection connection = getMySQL().connection;

    try (final Statement statement = connection.createStatement();
         final ResultSet resultSet = statement.executeQuery("SELECT UUID, name FROM Playerstatus")) {
      while (resultSet.next()) {
        final UUID uuid = UUID.fromString(resultSet.getString(1));
        final String name = resultSet.getString(2);
        cache.put(uuid, name);
      }
    } catch (final SQLException ex) {
      ex.printStackTrace();
    }

    return cache;
  }

  public Map<UUID, Group> getGroups() {
    final Map<UUID, Group> groups = new HashMap<>();
    final Connection connection = getMySQL().connection;

    try (final Statement statement = connection.createStatement();
         final ResultSet resultSet = statement.executeQuery("SELECT UUID, `Group` FROM BungeeGroupManager")) {
      while (resultSet.next()) {
        final UUID uuid = UUID.fromString(resultSet.getString(1));
        final Group group = Group.valueOf(resultSet.getString(2));
        groups.put(uuid, group);
      }
    } catch (final SQLException e) {
      e.printStackTrace();
    }
    return groups;
  }

  /**
   * Setzt einen Spieler nach dem Join in den Speicher (Playerstatus) bzw.
   * aendert dessen Namen nach Nickaenderung
   *
   * @param target gejointer Spieler
   */
  public void updatePlayer(final Player target) {
    final Map<UUID, SurvivalPlayer> players = SurvivalData.getInstance().getPlayers();

    if (players.containsKey(target.getUniqueId())) { //schon mal angemeldet
      final String qry = "UPDATE Playerstatus SET name=?,online=? WHERE UUID=?";
      try (final PreparedStatement statement = sql.connection.prepareStatement(qry)) {
        statement.setString(1, target.getName());
        statement.setInt(2, 1);
        statement.setString(3, target.getUniqueId().toString());

        statement.executeUpdate();
      } catch (final SQLException ex) {
        ex.printStackTrace();
      }
    } else { //neu
      final String qry = "INSERT INTO Playerstatus (name, UUID, online) VALUES (?, ?, ?)";
      try (final PreparedStatement statement = sql.connection.prepareStatement(qry)) {
        statement.setString(1, target.getName());
        statement.setString(2, target.getUniqueId().toString());
        statement.setInt(3, 1);
        statement.executeUpdate();
      } catch (final SQLException ex) {
        ex.printStackTrace();
      }
    }
  }

  /**
   * Speicherung von Spielern
   */
  public void storePlayers() {
    getMySQL().openConnectionIfClosed();
    final Map<UUID, SurvivalPlayer> survivalPlayers = SurvivalData.getInstance().getPlayers();
    final Collection<SurvivalPlayer> players = survivalPlayers.values();
    try (final PreparedStatement statement = sql.connection
        .prepareStatement("UPDATE SurvivalPlayer SET MONEY=?, LICENCES=?, VOTES=?, MAXZONE=?, HOME=?, LEVELPLAYER=? WHERE UUID=?")) {

      for (final SurvivalPlayer survivalPlayer : players) {
        final StringJoiner licences = determineLicences(survivalPlayer);

        final Location playerHome = survivalPlayer.getHome();
        final String home = playerHome.getX() + "/" + playerHome.getY() + "/" + playerHome.getZ();

        updateAndExecuteStatement(statement, survivalPlayer, licences.toString(), home);
      }
    } catch (final SQLException ex) {
      ex.printStackTrace();
    }
    storeComplaints();
  }

  private void storeComplaints() {
    final Map<UUID, SurvivalPlayer> survivalPlayers = SurvivalData.getInstance().getPlayers();
    final Collection<SurvivalPlayer> players = survivalPlayers.values();

    try (final PreparedStatement statement = sql.connection.
        prepareStatement("DELETE FROM SurvivalPlayerComplaints")) {
      statement.executeUpdate();
    } catch (final SQLException ex) {
      ex.printStackTrace();
    }

    try (final PreparedStatement statement = sql.connection
        .prepareStatement("INSERT INTO SurvivalPlayerComplaints (UUID, ID, REASON, OPERATOR, DATE) VALUES ( ?, ?, ?, ?, ?)")) {
      for (final SurvivalPlayer player : players) {
        for (final Complaint complaint : player.getComplaints()) {
          statement.setString(1, complaint.getUuid().toString());
          statement.setInt(2, complaint.getId());
          statement.setString(3, complaint.getReason());
          statement.setString(4, complaint.getExecutor().toString());
          final java.util.Date utilDate = complaint.getDate();
          final java.sql.Date sqlDate = new java.sql.Date(utilDate.getTime());
          final DateFormat df = new SimpleDateFormat("YYYY-MM-dd hh:mm:ss");
          statement.setString(5, df.format(sqlDate));
          statement.executeUpdate();
        }
      }
    } catch (final SQLException ex) {
      ex.printStackTrace();
    }
  }

  private void updateAndExecuteStatement(final PreparedStatement statement, final SurvivalPlayer survivalPlayer, final String licences,
                                         final String home) throws SQLException {
    statement.setDouble(1, survivalPlayer.getMoney());
    statement.setString(2, licences);
    statement.setInt(3, survivalPlayer.getVotes());
    statement.setInt(4, survivalPlayer.getMaxzone());
    statement.setString(5, home);
    statement.setString(6, ObjectBuilder.getStringOf(survivalPlayer.getLevelPlayer()));
    statement.setString(7, survivalPlayer.getUuid().toString());
    statement.executeUpdate();
  }

  private StringJoiner determineLicences(final SurvivalPlayer survivalPlayer) {
    final StringJoiner licences = new StringJoiner(",");
    final List<SurvivalLicence> survivalLicences = survivalPlayer.getLicences();
    for (final SurvivalLicence licence : survivalLicences) {
      licences.add(licence.name());
    }

    return licences;
  }

  /**
   * Erstellt einen Neuen Spieler in der Datenbank
   *
   * @param survivalPlayer SurvivalPlayer
   */
  public void createPlayer(final SurvivalPlayer survivalPlayer) {
    try (final PreparedStatement statement = sql.connection
        .prepareStatement("INSERT INTO SurvivalPlayer (UUID, MONEY, VOTES, MAXZONE, LEVELPLAYER) VALUES (?, ?, ?, ?, ?)")) {
      statement.setString(1, survivalPlayer.getUuid().toString());
      statement.setDouble(2, survivalPlayer.getMoney());
      statement.setInt(3, survivalPlayer.getVotes());
      statement.setInt(4, survivalPlayer.getMaxzone());
      statement.setString(5, ObjectBuilder.getStringOf(survivalPlayer.getLevelPlayer()));
      statement.executeUpdate();
    } catch (final SQLException ex) {
      ex.printStackTrace();
    }
  }

  //<editor-fold desc="getter and setter">
  public AsyncMySQL.MySQL getMySQL() {
    return sql;
  }
  //</editor-fold>

  /**
   * MySQL-Infos
   */
  public class MySQL {

    private final String database, host, password, user;
    private final int port;
    private Connection connection;

    /**
     * Konstruktor
     *
     * @throws SQLException SQL-Ausnahme
     * @throws ClassNotFoundException Driver wurde nicht gefunden
     */
    MySQL() throws SQLException, ClassNotFoundException {
      this.host = HOST;
      this.port = PORT;
      this.user = USER;
      this.password = PASSWORD;
      this.database = DATABASE;

      this.openConnection();
    }

    /**
     * Erstellung einer Query eines Strings
     *
     * @param query Query als String
     */
    void queryUpdate(final String query) {
      openConnectionIfClosed();
      try (final PreparedStatement statement = connection.prepareStatement(query)) {
        queryUpdate(statement);
      } catch (final SQLException ex) {
        ex.printStackTrace();
      }
    }

    /**
     * Erstellung einer Tabelle
     */
    public void createTables() {
      queryUpdate("CREATE TABLE IF NOT EXISTS Votes (UUID varchar(40) NOT NULL, Time varchar(10)" +
          " NOT NULL, Website varchar(40) NOT NULL);");
      queryUpdate("CREATE TABLE IF NOT EXISTS SurvivalPlayer (UUID varchar(40) NOT NULL, MONEY " +
          "double, LICENCES varchar(10000), VOTES int(11), MAXZONE int(11), HOME varchar(64), " +
          "LEVELPLAYER varchar(1024));");
      queryUpdate("CREATE TABLE IF NOT EXISTS Playerstatus (id int PRIMARY KEY AUTO_INCREMENT, " +
          "UUID VARCHAR(45), name VARCHAR(20), online int(1), lastonline timestamp, firstjoin " +
          "timestamp, ip VARCHAR(20));");
    }

    /**
     * Update einer Query eines PreparedStatements
     *
     * @param statement PreparedStatement
     * @see java.sql.PreparedStatement
     */
    void queryUpdate(final PreparedStatement statement) {
      openConnectionIfClosed();

      try (final PreparedStatement preparedStatement = statement) {
        preparedStatement.executeUpdate();
      } catch (final SQLException ex) {
        ex.printStackTrace();
      }
    }

    /**
     * Erstellung einer Query eines Strings
     *
     * @param query Query als String
     * @return Stringvalue
     */
    String query(final String query) {
      openConnectionIfClosed();
      ResultSet resultSet = null;
      try {
        resultSet = connection.prepareStatement(query).executeQuery();
        if (resultSet.next()) {
          return resultSet.getString(1);
        }
      } catch (final SQLException ex) {
        ex.printStackTrace();
      } finally {
        if (resultSet != null) {
          try {
            resultSet.close();
          } catch (final SQLException ex) {
            ex.printStackTrace();
          }
        }
      }
      return null;
    }

    private void openConnectionIfClosed() {
      try {
        if (this.connection == null || !this.connection.isValid(10) || this.connection.isClosed()) {
          openConnection();
        }
      } catch (final SQLException | ClassNotFoundException ex) {
        ex.printStackTrace();
      }
    }

    /**
     * Erstellung einer Connection
     *
     * @throws ClassNotFoundException Driver wurde nicht gefunden
     * @throws SQLException MySQLAusnahme
     */
    void openConnection() throws ClassNotFoundException, SQLException {
      Class.forName("com.mysql.jdbc.Driver");
      this.connection = DriverManager.getConnection("jdbc:mysql://" + this.host + ":" + this.port +
          "/" + this.database, this.user, this.password);
    }

    /**
     * Schliessen einer Connection
     * <p>
     * !!! WICHTIG
     */
    public void closeConnection() {
      if (connection != null) {
        try {
          connection.close();
        } catch (final SQLException e) {
          e.printStackTrace();
        }
      }
    }

    private void update(final String statement) {
      executor.execute(() -> sql.queryUpdate(statement));
    }

  }

}
