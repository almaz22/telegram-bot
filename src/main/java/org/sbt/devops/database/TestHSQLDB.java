package org.sbt.devops.database;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class TestHSQLDB {

    private static final String PROPS_FILE = "src/main/resources/database.properties";
    Connection connection = null;

    public static void main(String[] args){

        TestHSQLDB test = new TestHSQLDB();
        if (!test.loadDriver()) return;
        if (!test.getConnection()) return;

        test.createTable();
        test.fillTable();
        test.printTable();
        //test.dropTable();
        test.closeConnection();
    }

    private boolean loadDriver() {
        try {
            Class.forName("org.hsqldb.jdbcDriver");
        } catch (ClassNotFoundException e) {
            System.out.println("Драйвер не найден");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean getConnection() {

        String path = "";
        String dbname = "";
        String login = "";
        String password = "";

        try {
            InputStream is = new FileInputStream(PROPS_FILE);
            Properties pr = new Properties();
            pr.load(is);

            //path = pr.getProperty("pathDB");
            //dbname = pr.getProperty("nameDB");
            path = "testHSQL/";
            dbname = "testHSQLDB";
            login = pr.getProperty("userDB");
            password = pr.getProperty("passwordDB");

        } catch (IOException e) {
            e.printStackTrace();
        }

        try {

            String connectionString = "jdbc:hsqldb:file:"+path+dbname;
            connection = DriverManager.getConnection(connectionString, login, password);

        } catch (SQLException e) {
            System.out.println("Соединение не создано");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void createTable() {
        try {
            Statement statement = connection.createStatement();
            String sql = "CREATE TABLE testTable (id IDENTITY , value VARCHAR(255))";
            statement.executeUpdate(sql);
        } catch (SQLException ignored) {
        }
    }

    private void fillTable() {
        Statement statement;
        try {
            statement = connection.createStatement();
            String sql = "INSERT INTO testTable (value) VALUES('Вася')";
            statement.executeUpdate(sql);
            sql = "INSERT INTO testTable (value) VALUES('Петя')";
            statement.executeUpdate(sql);
            sql = "INSERT INTO testTable (value) VALUES('Саша')";
            statement.executeUpdate(sql);
            sql = "INSERT INTO testTable (value) VALUES('Катя')";
            statement.executeUpdate(sql);
            sql = "INSERT INTO testTable (value) VALUES('Света')";
            statement.executeUpdate(sql);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateTable() {
        Statement statement;
        try {
            statement = connection.createStatement();
            String sql = "UPDATE testTable SET value = 'Алмаз' WHERE id = 0";
            statement.executeUpdate(sql);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void deleteTable() {
        Statement statement;
        try {
            statement = connection.createStatement();
            String sql = "TRUNCATE TABLE testTable";
            statement.executeQuery(sql);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void dropTable() {
        Statement statement;
        try {
            statement = connection.createStatement();
            String sql = "DROP TABLE testTable";
            statement.executeQuery(sql);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void printTable() {
        Statement statement;
        try {
            statement = connection.createStatement();
            String sql = "SELECT * FROM testTable";
            ResultSet resultSet = statement.executeQuery(sql);

            while (resultSet.next()) {
                System.out.println(resultSet.getInt(1) + " "
                        + resultSet.getString(2));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void closeConnection() {

        Statement statement;
        try {
            statement = connection.createStatement();
            String sql = "SHUTDOWN";
            statement.execute(sql);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}