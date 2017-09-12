package org.sbt.devops.database;

import org.sbt.devops.BuildVars;
import org.telegram.telegrambots.logging.BotLogger;
import java.sql.*;

/**
 * Created by SBT-Kamalov-AN on 07.09.2017.
 */
public class HSQLDBManager {

    private static final String LOGTAG = "HSQLDBManager";
    private static volatile HSQLDBManager instance;

    private Connection connection = null;

    private HSQLDBManager () {
        if (!this.loadDriver()) return;
        if (!this.getConnection()) return;

        this.createTable();
        this.closeConnection();
    }

    public static HSQLDBManager getInstance() {
        final HSQLDBManager currentInstance;
        if (instance == null) {
            synchronized (HSQLDBManager.class) {
                if (instance == null) {
                    instance = new HSQLDBManager();
                }
                currentInstance = instance;
            }
        } else {
            currentInstance = instance;
        }
        return currentInstance;
    }

    private boolean loadDriver() {
        try {
            Class.forName("org.hsqldb.jdbcDriver");
        } catch (ClassNotFoundException e) {
            System.out.println("Driver not found");
            BotLogger.error(LOGTAG, e);
            return false;
        }
        return true;
    }

    private boolean getConnection() {
        try {
            String connectionString = "jdbc:hsqldb:file:"+ BuildVars.pathDB + BuildVars.nameDB; // pathDB = "myPath/" , nameDB = "myDB"
            connection = DriverManager.getConnection(connectionString, BuildVars.userDB, BuildVars.passwordDB);
        } catch (SQLException e) {
            System.out.println("Connection not created");
            BotLogger.error(LOGTAG, e);
            return false;
        }
        return true;
    }

    private void closeConnection() {

        Statement statement;
        try {
            statement = connection.createStatement();
            String sql = "SHUTDOWN";
            statement.execute(sql);

        } catch (SQLException e) {
            BotLogger.error(LOGTAG, e);
        }
    }

    private void createTable() {
        try {
            Statement statement = connection.createStatement();
            String sql = "CREATE TABLE IF NOT EXISTS BuroState (id IDENTITY , userId INTEGER NOT NULL, chatId BIGINT NOT NULL, state INTEGER DEFAULT 0)";
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            BotLogger.error(LOGTAG, e);
        }
    }

    public int getState(Integer userId, Long chatId) {
        int state = 0;
        try {
            instance.getConnection();
            final PreparedStatement preparedStatement = connection.prepareStatement("SELECT state FROM BuroState WHERE userId = ? AND chatId = ?");
            preparedStatement.setInt(1, userId);
            preparedStatement.setLong(2, chatId);
            final ResultSet result = preparedStatement.executeQuery();
            if (result.next()) {
                state = result.getInt("state");
            }
            instance.closeConnection();
        } catch (SQLException e) {
            BotLogger.error(LOGTAG, e);
        }
        return state;
    }

    public boolean insertState(Integer userId, Long chatId, int state) {
        int updatedRows = 0;
        try {
            instance.getConnection();
            final PreparedStatement preparedStatement = connection.prepareStatement("MERGE INTO BuroState AS t " +
                    "USING (VALUES(?,?,?)) AS vals(a,b,c) " +
                    "ON t.userId = vals.a AND t.chatId = vals.b " +
                    "WHEN MATCHED THEN " +
                    "UPDATE SET t.state = vals.c " +
                    "WHEN NOT MATCHED THEN " +
                    "INSERT (userId, chatId, state) VALUES  vals.a, vals.b, vals.c");
            preparedStatement.setInt(1, userId);
            preparedStatement.setLong(2, chatId);
            preparedStatement.setInt(3, state);
            updatedRows = preparedStatement.executeUpdate();

            instance.closeConnection();
        } catch (SQLException e) {
            BotLogger.error(LOGTAG, e);
        }
        return updatedRows > 0;
    }
}
