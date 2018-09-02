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
			String strDate = /* "2018-09-07 09:00" */"2018-08-22 22:57";
			wTime = dateFormat.parse(strDate);
			strDate = /* "2018-09-07 12:00" */"2018-08-22 22:57";
			mTime = dateFormat.parse(strDate);
		} catch (ParseException e) {
			e.printStackTrace();
		}

		try {
			Class.forName("com.mysql.jdbc.Driver");
			con = DriverManager.getConnection(
					"jdbc:mysql://cwsw.cheongwon10.cafe24.com/cheongwonpaydb?useUnicode=true&characterEncoding=utf-8",
					"testuser", "testuserpassword");// DB서버(MySQL)주소와
			// 계정,
			// 패스워드
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
			userList.add(newUser);
		}
	}
	// 서버 시작 및 운영파트 끝
}

class User extends Thread {

	public static final int OP_LOGIN = 1, OP_PURCHASE = 2, OP_ADD_ITEM = 3, OP_RF_BAL = 4, OP_GET_GOODS_LIST = 5,
			OP_GET_REFUND_LIST = 6, OP_REFUND = 7, OP_ADD_MEMBER = 10, OP_EDIT_GOODS = 11, OP_DELETE_GOODS = 12,
			OP_EDIT_PW = 13, OP_GET_NAME = 14, OP_CHARGE = 16, OP_CHANGEINFO = 17, OP_CLUB_INCOME = 18, OP_PAYBACK = 19,
			OP_LOST_REPORT = 20, OP_CANCEL_LOST = 21, OP_SET_ATD = 22, OP_RS_NOTIME = 103,
			OP_PURCHASE_RS_OVERLIMIT = 104, OP_PURCHASE_RS_USERNULL = 106, OP_RS_SUCCESS = 105,
			OP_RS_ALREADY_ROLL = 107, OP_EXIT = 1110;
	// 통신시 이용하는 명령 코드(OP-Code)를 정수 데이터타입으로 저장한다.
	public static final String OP_GET_GOODS_LIST_FIN = "##";// 통신시 이용하는 명령 코드(OP-Code)를 문자 데이터타입으로 저장한다. 이 코드만 문자형으로
															// 사용하는이유는 아래에서 DataInputStream할 때 문자형으로 불러오기 때문이다.

	String userName;
	Socket socket;

	DataInputStream dis;
	DataOutputStream dos;

	public User(String s, Socket sc) {
		userName = s;
		socket = sc;
	}

	@Override
	public void run() {

		try {

			dis = new DataInputStream(socket.getInputStream());
			dos = new DataOutputStream(socket.getOutputStream());

		} catch (IOException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
			CheongwonPayServer.userList.remove(this);
			return;
		}

		// String readData = null;// 받은 데이터를 문자 데이터타입으로 저장한다. -> 리팩토링 이후 사라짐
		int readOPData = 0;// 받은 OP-Code를 정수 배열로 저장한다.

		System.out.println("Socket Opened!");
		CheongwonPayDB dbHandler = new CheongwonPayDB(userName);

		while (true) {
			try {
				try {
					readOPData = dis.readInt();
					System.out.print("readOPData : " + readOPData);
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}

				if (readOPData == OP_LOGIN) {// 로그인
					System.out.println(" : OP_LOGIN");

					try {

						String Club_Name = null;
						String data = dis.readUTF();
						boolean isSuccess = dbHandler.login(data);
						Club_Name = dbHandler.get_club_name(data);
						if (isSuccess) {
							dos.writeUTF("Login Success!");
							dos.writeUTF(Club_Name);
						} else {
							dos.writeUTF("Login Failed!");
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_PURCHASE) {// 거래, 출석체크
					System.out.println(" : OP_PURCHASE");

					try {
						String input = dis.readUTF();
						String user = input.split(":")[0];
						int result = dbHandler.purchase(input);
						if (result != -1) {
							dos.writeInt(result);
						}
						if (result == OP_RS_SUCCESS) {
							dbHandler.set_atd(user);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_ADD_ITEM) {// 상품추가
					System.out.println(" : OP_ADD_ITEM");

					try {
						dbHandler.addItem(dis.readUTF());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_RF_BAL) {// 잔액, 출석수 조회 + User리스트에 없을경우(팔찌)추가
					System.out.println(" : OP_RF_BAL");

					try {
						String result = dbHandler.getBalanceATD(dis.readUTF());
						dos.writeUTF(result);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_GET_GOODS_LIST) {// 상품목록불러오기 goodsnum,name,price주기
					System.out.println(" : OP_GET_GOODS_LIST");

					try {
						String[] goodsList = dbHandler.getGoodsList();
						for (String nextGood : goodsList) {
							dos.writeUTF(nextGood);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_GET_REFUND_LIST) {// get사용자결제목록 for refund time,goodsnum,name,price주기
					System.out.println(" : OP_GET_REFUND_LIST");

					try {
						String[] refundList = dbHandler.getRefundList(dis.readUTF());
						for (String nextList : refundList) {
							dos.writeUTF(nextList);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}

				}
				if (readOPData == OP_REFUND) {// 결체취소 (+돈복구)
					System.out.println(" : OP_REFUND");

					try {
						dbHandler.refund(dis.readUTF());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_EDIT_GOODS) {// 상품목록변경
					System.out.println(" : OP_EDIT_GOODS");

					try {
						dbHandler.edit_goods(dis.readUTF());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_DELETE_GOODS) {// 상품삭제

					System.out.println(" : OP_DELETE_GOODS");

					try {
						dbHandler.delete_goods(dis.readUTF());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_EDIT_PW) {// 비밀번호 변경
					System.out.println(" : OP_EDIT_PW");

					try {
						dbHandler.edit_password(dis.readUTF());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_GET_NAME) {// 이름 조회
					System.out.println(" : OP_GET_NAME");

					try {
						String result = dbHandler.get_username(dis.readUTF());
						dos.writeUTF(result);
						System.out.println(result);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_CHARGE) {
					System.out.println(" : OP_CHARGE");

					try {
						dbHandler.charge(dis.readUTF());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_CHANGEINFO) {
					System.out.println(" : OP_CHANGEINFO");

					try {
						dbHandler.change_info(dis.readUTF());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (readOPData == OP_CLUB_INCOME) {
					System.out.println(" : OP_CLUB_INCOME");

					try {
						int result = dbHandler.get_club_income();
						dos.writeInt(result);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_PAYBACK) {
					System.out.println(" : OP_PAYBACK");

					try {
						dbHandler.payback(dis.readUTF());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_ADD_MEMBER) {
					System.out.println(" : OP_ADD_MEMBER");

					try {
						dbHandler.add_member(dis.readUTF());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_LOST_REPORT) {
					System.out.println(" : OP_LOST_REPORT");

					try {
						int result = dbHandler.lost_report(dis.readUTF());
						dos.writeInt(result);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_CANCEL_LOST) {
					System.out.println(" : OP_CANCEL_LOST");

					try {
						int result = dbHandler.cancel_lost(dis.readUTF());
						dos.writeInt(result);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_SET_ATD) {
					System.out.println(" : OP_SET_ATD");

					try {
						int result = dbHandler.set_atd(dis.readUTF());
						dos.writeInt(result);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				if (readOPData == OP_EXIT) {
					System.out.println(" : OP_EXIT");
					break;
				}
			} catch (Exception e) {
				e.printStackTrace();
				break;
			}

		} // 스레드 종료 부분
		System.out.println("thread closed");
		try {
			dos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			dis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		CheongwonPayServer.userList.remove(this);
	}

}

class CheongwonPayDB {
	String userName;

	int Club_Num;
	String School_Type;

	public CheongwonPayDB(String u) {
		userName = u;
	}

	boolean simplifiedFDS(String School_Type) {// 사기거래탐지시스템(FDS) 축제시간의 경우 true, 축제시간이외의 경우 false를 반환..
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

	boolean login(String readData) {
		boolean toReturn = false;
		try {// 로그인시 DataInputStream 형식이 ID:PW 형식이다.
			System.out.println("ID:PW : " + readData);
			// 받은 정보를 서버에서 처리할 수 있도록 각각 분리하여 저장한다.
			String ID = readData.split(":")[0];
			String PW = readData.split(":")[1];

			String club_name;
			java.sql.PreparedStatement st = null;
			ResultSet rs = null;
			st = CheongwonPayServer.con.prepareStatement("SELECT * FROM club WHERE Name=?");
			st.setString(1, ID);

			if (st.execute()) {// "ID"와 일치하는 데이터를 불러오기..
				rs = st.getResultSet();
			}

			String password = null;// "password"은 옳은 패스워드, "Club_Name"은 동아리명을 문자 데이터타입으로 저장한다.

			while (rs.next()) {
				// 옳은 패스워드 불러오기
				password = rs.getString("PW");
				System.out.println("realPW : " + password);

				// 동아리 이름 불러오기
				club_name = rs.getString("Name");
				System.out.println("Club_Name : " + club_name);

				// 동아리고유번호저장
				Club_Num = rs.getInt("Club_Num");
				System.out.println("Club_Num : " + Club_Num);

				// 동아리 소속 학교구분 저장
				School_Type = rs.getString("School");
				System.out.println("School_Type : " + School_Type);
			}

			if (password != null && password.equals(PW)) {// 로그인성공했을때
				System.out.println("Login Success!");
				toReturn = true;

			} else {// 로그인 실패
				System.out.println("Login Failed!");
				System.out.println("db PW : " + password + " .");
				System.out.println("PW    : " + PW + " .");
			}

			st.close();

		} catch (SQLException sqex) {// SQL통신 중 오류가 생겼을 때 로그 기록
			System.out.println("SQLException: " + sqex.getMessage());
			System.out.println("SQLState: " + sqex.getSQLState());
		}
		return toReturn;
	}

	String get_club_name(String data) {
		String club_name = null;
		String readData = data;
		String id = readData.split(":")[0];

		club_name = id;
		return club_name;

	}

	int purchase(String data) {
		String student = data.split(":")[0]; // 바코드 데이터
		int Goods_Num = Integer.parseInt(data.split(":")[1]);
		System.out.println("User : " + student);
		System.out.println("Goods_Num : " + Goods_Num);
		int toReturn;

		int Balance = 0, Price = 0;// "Balance"는 잔액, "Price"는 상품가를 정수 데이터타입으로 저장한다.

		if (student.equals(null)) {// 바코드데이터가 null일 때
			toReturn = User.OP_PURCHASE_RS_USERNULL;
		} else {
			try {
				if (simplifiedFDS(School_Type)) {// 축제시간일 때
					java.sql.Statement st = null;
					ResultSet rs = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);

					// 잔액조회
					if (st.execute("SELECT Balance FROM user WHERE User='" + student + "'")) {
						rs = st.getResultSet();
					}
					// 잔액 추가 if문 입력

					while (rs.next()) {
						Balance = rs.getInt(1);
						System.out.println("Balance : " + Balance);
					}

					// 상품가격조회
					if (st.execute("SELECT Price FROM goods WHERE Goods_Num='" + Goods_Num + "'")) {
						rs = st.getResultSet();
					}
					while (rs.next()) {
						Price = rs.getInt(1);
						System.out.println("Price : " + Price);
					}

					// 거래가능조건 잔액>=상품가
					if (Balance >= Price) {
						// 거래내역추가
						st.execute("INSERT INTO transactions (User, Club_Num, Goods_Num, Price) VALUES ('" + student
								+ "'," + Club_Num + "," + Goods_Num + "," + Price + ")");

						// Balance차감
						st.execute("UPDATE user SET Balance=(Balance-" + Price + ") WHERE User='" + student + "'");

						// 동아리 수익증가
						st.execute("UPDATE club SET Income=(Income+" + Price + ") WHERE Club_Num='" + Club_Num + "'");

						// 거래성공
						toReturn = User.OP_RS_SUCCESS;
						// 원래는 출석체크
					} else {
						// 거래실패 잔액부족
						toReturn = User.OP_PURCHASE_RS_OVERLIMIT;
					}
					st.close();
				} else {// 축제시간 이외에 요청
					toReturn = User.OP_RS_NOTIME;
				}
			} catch (SQLException e) {
				Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, e);
				toReturn = -1;
			}
		}
		return toReturn;
	}

	void addItem(String readData) {
		try {
			System.out.println("Goods_Name, Price : " + readData);

			// 받은 정보를 서버에서 처리할 수 있도록 각각 분리하여 저장한다.
			String Goods_Name = readData.split(":")[0];
			int Price = Integer.parseInt(readData.split(":")[1]);

			java.sql.Statement st = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			st.execute("INSERT INTO goods (Club_Num, Goods_Name, Price) VALUES ('" + Club_Num + "','" + Goods_Name
					+ "','" + Price + "')");// 데이터베이스에 동아리고유번호, 상품명, 상품가로 데이터를 저장한다..
			st.close();

		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	String getBalanceATD(String readData) {
		String toReturn = null;
		try {
			System.out.println("User : " + readData);

			java.sql.Statement st = null;
			ResultSet rs = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

			if (st.execute("SELECT * FROM user WHERE User='" + readData + "'")) {// 바코드에 일치하는 학생데이터 불러오기
				rs = st.getResultSet();
			}
			// 팔찌미등록시 X
			if (!rs.next()) {// 불러온 학생데이터가 없을 때
				// User리스트에 추가
				rs.close();
				st.execute("INSERT INTO user (User) VALUES ('" + readData + "')");
				if (st.execute("SELECT * FROM user WHERE User='" + readData + "'")) {
					rs = st.getResultSet();
				}
			}

			rs.beforeFirst();

			while (rs.next()) {
				toReturn = String.valueOf(rs.getInt("Balance"));
				toReturn += ":";
				toReturn += String.valueOf(rs.getInt("ATD_Count"));
			}
			
			
			st.close();
			System.out.println(toReturn);
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
		return toReturn;
	}

	String[] getGoodsList() {
		java.sql.Statement st = null;
		ResultSet rs = null;
		ArrayList<String> toReturn = new ArrayList<>();

		try {
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

			if (st.execute("SELECT * FROM goods WHERE Club_Num='" + Club_Num + "'")) {// 동아리고유번호에 일치하는 상품들 불러오기
				rs = st.getResultSet();
			}
			while (rs.next()) {
				toReturn.add(rs.getInt("Goods_Num") + ":" + rs.getString("Goods_Name") + ":" + rs.getInt("Price"));// 상품고유번호,
																													// 상품명
																													// 상품가를
																													// 전송

			}
			toReturn.add(User.OP_GET_GOODS_LIST_FIN);// 상품목록 전송종료OP전송

			st.close();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
		return toReturn.toArray(new String[toReturn.size()]);
	}

	String[] getRefundList(String readData) {
		java.sql.Statement st = null;
		ResultSet rs = null;
		java.sql.Statement st2 = null;
		ResultSet rs2 = null;
		ArrayList<String> toReturn = new ArrayList<>();
		try {

			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			st2 = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

			if (st.execute("SELECT * FROM transactions WHERE Club_Num=" + Club_Num + " and User='" + readData + "'")) {
				rs = st.getResultSet();
			}

			while (rs.next()) {
				int goods_num = rs.getInt("Goods_Num");
				st2.execute("SELECT Goods_Name FROM goods WHERE Goods_Num=" + goods_num);
				rs2 = st2.getResultSet();
				rs2.next();
				if (rs.getString("Cancel") != null)
					continue;
				toReturn.add(rs.getString("Num") + ":" + rs.getString("Price") + ":" + rs2.getString(1));// goods_name
			}
			toReturn.add(User.OP_GET_GOODS_LIST_FIN);// 상품목록전송종료OP

			st.close();
			st2.close();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
		return toReturn.toArray(new String[toReturn.size()]);
	}

	void refund(String readData) {

		try {
			java.sql.Statement st = null;
			ResultSet rs = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			rs = st.executeQuery("SHOW DATABASES");

			String temp[] = { null, null, null };// 기취소여부:바코드:상품가

			// 이미취소된결제인지 확인
			if (st.execute("SELECT Cancel FROM transactions WHERE Num='" + readData + "'")) {
				rs = st.getResultSet();
			}
			while (rs.next()) {
				temp[0] = rs.getString(1);// 현재 SQL버전이 NCHAR등을 지원하지 않아서 바꿈.
			}

			if (temp[0] == null) {
				// Num을통해 user바코드확인
				if (st.execute("SELECT User FROM transactions WHERE Num='" + readData + "'")) {
					rs = st.getResultSet();
				}
				while (rs.next()) {
					temp[1] = rs.getString(1);
				}

				// Num을통해 가격확인
				if (st.execute("SELECT Price FROM transactions WHERE Num='" + readData + "'")) {
					rs = st.getResultSet();
				}
				while (rs.next()) {
					temp[2] = rs.getString(1);
				}

				// 거래취소표시
				if (st.execute("UPDATE transactions SET Cancel = '1' WHERE Num='" + readData + "'"))
					;

				// Balance 복구
				if (st.execute("UPDATE user SET Balance = (Balance+" + temp[2] + ") WHERE User='" + temp[1] + "'"))
					;

				// 동아리 Income 감소
				if (st.execute("UPDATE club SET Income = (Income-" + temp[2] + ") WHERE Club_Num='" + Club_Num + "'"))
					;

				System.out.println("refunded : User : " + temp[1] + ", Balance : " + temp[2]);

				st.close();
			} else {
			}
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	void edit_goods(String readData) {

		try {
			int Goods_Num = Integer.parseInt(readData.split(":")[0]);
			String Goods_Name = readData.split(":")[1];
			int Price = Integer.parseInt(readData.split(":")[2]);

			java.sql.Statement st = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

			System.out.println(readData);

			st.execute("UPDATE goods SET Goods_Name='" + Goods_Name + "', Price='" + Price + "' WHERE Goods_Num='"
					+ Goods_Num + "'");
			st.close();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	void delete_goods(String readData) {
		try {
			System.out.println(readData);

			java.sql.Statement st = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

			st.execute("DELETE FROM goods WHERE Goods_Num=" + readData);
			st.close();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	void edit_password(String readData) {
		try {
			System.out.println("after pw: " + readData);

			java.sql.Statement st = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

			st.execute("UPDATE club SET PW='" + readData + "' WHERE Club_Num='" + Club_Num + "'");// 패스워드를
																									// 변경한다.

			st.close();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	String get_username(String readData) {
		String toReturn = null;
		try {
			System.out.println("User : " + readData);

			java.sql.Statement st = null;
			ResultSet rs = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

			if (st.execute("SELECT Name FROM user WHERE User='" + readData + "'")) {// 바코드에 일치하는 학생명 불러오기
				rs = st.getResultSet();
			}

			while (rs.next()) {
				String str = rs.getString(1);
				if (str != null) {// null이 아닐 때
					toReturn = str;// 이름값을 전송한다.
				}
			}

			if (st.execute("SELECT Lost FROM user WHERE User='" + readData + "'")) {// 분실여부 확인
				rs = st.getResultSet();
			}

			rs.next();
			int lost = rs.getInt(1);
			if (toReturn != null) {
				toReturn = String.join(":", Integer.toString(lost), toReturn);
			}

			st.close();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
		if (toReturn == null) {
			toReturn = "lookup error!";
		}
		return toReturn;
	}

	void charge(String readData) {
		try {
			String user = readData.split(":")[0];
			String wtbalance = readData.split(":")[1];// wtbalance가 충전될 가격.
			java.sql.Statement st = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

			System.out.println("User : " + user);
			System.out.println("Balance : " + wtbalance);

			if (user.equals("null")) {// 바코드데이터가 null일 때
				return;
			} else {// 충전성공
				st.execute("UPDATE user SET Balance=(Balance+" + wtbalance + ") WHERE User='" + user + "'");
			}
			st.close();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	void change_info(String readData) {
		try {
			String User = readData.split(":")[0];
			String newName = readData.split(":")[1];
			System.out.println("name = " + newName);
			String newSchool = readData.split(":")[2];
			int newGrade = Integer.parseInt(readData.split(":")[3]);
			int newClass = Integer.parseInt(readData.split(":")[4]);
			int newNumber = Integer.parseInt(readData.split(":")[5]);

			java.sql.Statement st = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

			st.execute("UPDATE user SET Name='" + newName + "', School='" + newSchool + "',Grade=" + newGrade
					+ ",Class=" + newClass + ",Number=" + newNumber + " WHERE User='" + User + "'");

			st.close();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	int get_club_income() {
		int toReturn = 0;
		try {
			java.sql.Statement st = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			ResultSet rs = null;

			if (st.execute("SELECT Income FROM club WHERE Club_Num=" + Club_Num)) {
				rs = st.getResultSet();
			}
			rs.next();
			toReturn = rs.getInt(1);

			st.close();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
		return toReturn;
	}

	void payback(String readData) {
		try {
			java.sql.Statement st = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

			st.execute("UPDATE user SET Balance=0 WHERE User='" + readData + "'");

			st.close();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	void add_member(String readData) {
		try {
			java.sql.Statement st = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			System.out.println("OP_ATD : " + readData);
			st.execute("UPDATE user SET Club_Num='" + Club_Num + "' WHERE User='" + readData + "'");

			st.close();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	int lost_report(String readData) {
		int toReturn = -1;

		try {
			java.sql.Statement st = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			String name = readData.split(":")[0];
			String school = readData.split(":")[1];
			int grade = Integer.parseInt(readData.split(":")[2]);
			int num_class = Integer.parseInt(readData.split(":")[3]);
			int number = Integer.parseInt(readData.split(":")[4]);
			ResultSet rs = null;

			if (st.execute("SELECT Lost,Num FROM user WHERE Name='" + name + "' AND School='" + school + "' AND Grade="
					+ grade + " AND Class=" + num_class + " AND Number=" + number)) {
				rs = st.getResultSet();
			}
			if (rs.next()) {
				int isLost = rs.getInt("Lost");
				if (isLost == 1) {
					toReturn = 1;
				} else {
					st.execute("UPDATE user SET Lost=1 WHERE Num=" + rs.getInt("Num"));
					toReturn = 0;
				}
			}

			st.close();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}

		return toReturn;
	}

	int cancel_lost(String readData) {
		int toReturn = -1;

		try {
			java.sql.Statement st = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			ResultSet rs = null;

			if (st.execute("SELECT Lost FROM user WHERE User='" + readData + "'")) {
				rs = st.getResultSet();
			}
			if (rs.next()) {
				int isLost = rs.getInt("Lost");
				if (isLost == 0) {
					toReturn = 1;
				} else {
					st.execute("UPDATE user SET Lost=0 WHERE User='" + readData + "'");
					toReturn = 0;
				}
			}

			st.close();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
		return toReturn;
	}

	int set_atd(String readData) {
		int toReturn = User.OP_RS_NOTIME;
		if (readData.equals(null)) {
			toReturn = User.OP_PURCHASE_RS_USERNULL;
		} else {
			if (simplifiedFDS(School_Type)) {
				try {
					java.sql.Statement st = null;
					ResultSet rs = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);
					int user_num = 0;

					if (st.execute("SELECT Num FROM user WHERE User='" + readData + "'")) {
						rs = st.getResultSet();
					}
					if (rs.next()) {
						user_num = rs.getInt(1);
					} else {
						rs.close();
						st.execute("INSERT INTO user (User) VALUES ('" + readData + "')");
						if (st.execute("SELECT Num FROM user WHERE User='" + readData + "'")) {
							rs = st.getResultSet();
						}
					}

					if (st.execute(
							"SELECT 1 FROM atd_history WHERE User_Num=" + user_num + " AND Club_Num=" + Club_Num)) {
						rs = st.getResultSet();
					}
					if (!rs.next()) {
						st.execute("INSERT INTO atd_history (User_Num, Club_Num) VALUES (" + user_num + ", " + Club_Num
								+ ")");
						st.execute("UPDATE user SET ATD_Count=(ATD_count+1) WHERE Num=" + user_num);
						toReturn = User.OP_RS_SUCCESS;
					} else {
						toReturn = User.OP_RS_ALREADY_ROLL;
					}

					st.close();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
		return toReturn;
	}
}
