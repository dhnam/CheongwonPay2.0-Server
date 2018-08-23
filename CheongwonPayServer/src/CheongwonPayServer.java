import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;

public class CheongwonPayServer {
	public static final int port = 923;// 서버 포트를 지정해주었다.
	public static ArrayList<User> userList;// 접속 클라이언트 디바이스를 배열로 저장한다.
	public static Connection con = null;
	public static java.util.Date mTime, wTime;

	public static void main(String[] args) throws IOException {
		ServerSocket server;
		Socket newClient;
		User newUser;
		userList = new ArrayList<User>();
		server = new ServerSocket(port);

		try {// 사기거래탐지시스템(FDS)에서 이용할 남고, 여고 축제 시작시간을 Date타입으로 변환하여 저장한다.
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());

		} catch (ParseException e) {
			e.printStackTrace();
		}

		try {
			Class.forName("com.mysql.jdbc.Driver");

			// DB연결
		} catch (SQLException sqex) {
			System.out.println("SQLException: " + sqex.getMessage());
			System.out.println("SQLState: " + sqex.getSQLState());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}


		while (true) {
			System.out.println("Waiting for client...");
			newClient = server.accept();
			newUser = new User("unknown", newClient);
			newUser.start();// 유저 접속시 유저를 ArrayList에 추가

	}

	boolean simplifiedFDS(String School_Type) {// 사기거래탐지시스템(FDS) 축제시간의 경우 true, 축제시간이외의 경우 false를 반환.
		// 현재시간 측정
		long now = System.currentTimeMillis();
		java.util.Date currentTime = new java.util.Date(now);

		// 축제시간의 거래인지 확인
		switch (School_Type) {
		case "M":// 남고일 때
			if (currentTime.compareTo(CheongwonPayServer.mTime) < 0) {
				return false;
			}
			break;
		case "W":// 여고일 때
			if (currentTime.compareTo(CheongwonPayServer.wTime) < 0) {
				return false;
			}
			break;
		case "U":// 연합동아리일 때
			if (currentTime.compareTo(CheongwonPayServer.wTime) < 0) {
				return false;
			}
			break;
		}
		return true;
	}


					java.sql.Statement st = null;
					ResultSet rs = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);


