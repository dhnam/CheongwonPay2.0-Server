import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Calendar;
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
			String strDate = "2018-09-07 09:00";
			wTime = dateFormat.parse(strDate);
			strDate = "2018-09-07 12:00";
			mTime = dateFormat.parse(strDate);
		} catch (ParseException e) {
			e.printStackTrace();
		}

		try {
			Class.forName("com.mysql.jdbc.Driver");
			con = DriverManager.getConnection("jdbc:mysql://localhost/cheongwonpaydb", "testuser", "testuserpassword");// DB서버(MySQL)주소와
																														// 계정,
																														// 패스워드
			// DB연결
		} catch (SQLException sqex) {
			System.out.println("SQLException: " + sqex.getMessage());
			System.out.println("SQLState: " + sqex.getSQLState());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		java.sql.Statement str = null;

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
			OP_GET_REFUND_LIST = 6, OP_REFUND = 7, OP_ATD = 10, OP_EDIT_GOODS = 11, OP_DELETE_GOODS = 12,
			OP_EDIT_PW = 13, OP_GET_NAME = 14, OP_USER_MATCHING = 15, OP_CHARGE = 16, OP_CHANGEINFO = 17,
			OP_REFUND_RS_ALREADY_REFUNDED = 101, OP_PURCHASE_RS_NOTIME = 103, OP_PURCHASE_RS_OVERLIMIT = 104,
			OP_PURCHASE_RS_USERNULL = 106, OP_PURCHASE_RS_SUCCESS = 105, OP_CHARGE_RS_USERNULL = 107,
			OP_CHARGE_RS_SUCCESS = 108, OP_EXIT = 1110;// 통신시
														// 이용하는
														// 명령
														// 코드(OP-Code)를
														// 정수
														// 데이터타입으로 저장한다.
	public static final String OP_GET_GOODS_LIST_FIN = "##";// 통신시 이용하는 명령 코드(OP-Code)를 문자 데이터타입으로 저장한다. 이 코드만 문자형으로
															// 사용하는
															// 이유는 아래에서 DataInputStream할 때 문자형으로 불러오기 때문이다

	String userName;
	Socket socket;

	DataInputStream dis;
	DataOutputStream dos;

	public User(String s, Socket sc) {
		userName = s;
		socket = sc;
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

		String readData = null;// 받은 데이터를 문자 데이터타입으로 저장한다.
		int readOPData = 0;// 받은 OP-Code를 정수 배열로 저장한다.

		int Club_Num = 0;// 동아리 고유번호를 정수 배열로 저장한다.
		String School_Type = null;// 학교구분(남고는 M, 여고는 W, 연합동아리는 U)을 문자 데이터타입으로 저장한다.
		System.out.println("Socket Opened!");
		CheongwonPayDB dbHandler = new CheongwonPayDB(userName);

		while (true) {
			try {
				readOPData = dis.readInt();
				System.out.println("readOPData : " + readOPData);
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}

			if (readOPData == OP_LOGIN) {// 로그인
				String Club_Name = null;
				try {
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
				try {
					int result = dbHandler.purchase(dis.readUTF());
					if (result != -1) {
						dos.writeInt(result);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (readOPData == OP_ADD_ITEM) {// 상품추가
				try {
					dbHandler.addItem(dis.readUTF());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (readOPData == OP_RF_BAL) {// 잔액, 출석수 조회 + User리스트에 없을경우(팔찌)추가
				try {
					String result = dbHandler.getBalanceVisits(dis.readUTF());
					if (result != null) {
						dos.writeUTF(result);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (readOPData == OP_GET_GOODS_LIST) {// 상품목록불러오기 goodsnum,name,price주기
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

				try {
					int result = dbHandler.refund(dis.readUTF());
					if (result != 0 && result != -1) {
						dos.writeInt(result);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (readOPData == OP_ATD) {// 출석체크 부원등록
				try {
					dbHandler.attendance(dis.readUTF());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (readOPData == OP_EDIT_GOODS) {// 상품목록변경
				try {
					dbHandler.edit_goods(dis.readUTF());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (readOPData == OP_DELETE_GOODS) {// 상품삭제
				try {
					readData = dis.readUTF();// 클라이언트가 보낸 상품고유번호를 "readData"에 저장한다.
					System.out.println(readData);

					if (!simplifiedFDS(School_Type)) {// 축제시간 이외일 때
						java.sql.Statement st = null;
						st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
								ResultSet.CONCUR_READ_ONLY);

						st.execute("DELETE From goods where Goods_Num='" + readData + "'");
						st.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			if (readOPData == OP_EDIT_PW) {// 패스워드 변경
				try {
					readData = dis.readUTF();// 클라이언트가 보낸 변경할 패스워드를 "readData"에 저장한다.
					System.out.println("after pw: " + readData);

					java.sql.Statement st = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);

					st.execute("UPDATE club set PW='" + readData + "' where Club_Num='" + Club_Num + "'");// 패스워드를
																											// 변경한다.
					st.close();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			if (readOPData == OP_GET_NAME) {// 이름조회
				try {
					readData = dis.readUTF();// 클라이언트가 보낸 바코드을 "readData"에 저장한다.
					System.out.println("User : " + readData);

					java.sql.Statement st = null;
					ResultSet rs = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);

					if (st.execute("SELECT Name FROM user where User='" + readData + "'")) {// 바코드에 일치하는 학생명 불러오기
						rs = st.getResultSet();
					}

					while (rs.next()) {
						String str = rs.getNString(1);
						if (str != null) {// null이 아닐 때
							dos.writeUTF(str);// 이름값을 전송한다.
						} else {
							dos.writeUTF("lookup error!");// "lookup error!"이라고 전송한다.
						}
					}

					st.close();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			/*if (readOPData == OP_USER_MATCHING) {// 유저매칭
				try {
					readData = dis.readUTF();// 클라이언트가 보낸 바코드, 학번, 학교구분 값을 "readData"에 저장한다.
					// 받은 정보를 서버에서 처리할 수 있도록 각각 분리하여 저장한다.
					String Barcode = readData.split(":")[0];
					String Student_ID = readData.split(":")[1];
					String Type = readData.split(":")[2];
					
					String Grade = Student_ID.substring(0, 1);
					String Class = Student_ID.substring(1, 3);
					String Number = Student_ID.substring(3);
					System.out.println("User : " + Barcode);
					System.out.println("Student_ID : " + Grade + ":" + Class + ":" + Number);

					java.sql.Statement st = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);

					switch (Type) {
					case "1":// 남고일 때
						st.execute("UPDATE user set User='" + Barcode + "' where Grade='" + Grade + "' and Class='"
								+ Class + "' and Number='" + Number + "' and School='M'");
						break;
					case "2":// 여고일 때
						st.execute("UPDATE user set User='" + Barcode + "' where Grade='" + Grade + "' and Class='"
								+ Class + "' and Number='" + Number + "' and School='W'");
						break;
					}
					st.close();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}*/

			if (readOPData == OP_CHARGE) {
				// 잔액 충전시 코드
				// user:wtbalance형식으로 dis;
				try {
					readData = dis.readUTF();

					String User = readData.split(":")[0];
					String wtbalance = readData.split(":")[1];// wtbalance가 충전될 가격.

					java.sql.Statement st = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);

					System.out.println("User : " + User);
					System.out.println("Balance : " + wtbalance);

					if (User.equals("null")) {// 바코드데이터가 null일 때
						dos.writeInt(OP_CHARGE_RS_USERNULL);
					} else {// 충전성공
						st.execute("UPDATE user SET Balance=(Balance+" + wtbalance + ") where User='" + User + "'");
						dos.writeInt(OP_CHARGE_RS_SUCCESS);
					}
					st.close();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			
			  if (readOPData == OP_CHANGEINFO) {
					  try {
						readData = dis.readUTF();
  
						String User = readData.split(":")[0];
						String newName = readData.split(":")[1];
						String newSchool = readData.split(":")[2];
						int newGrade = Integer.parseInt(readData.split(":")[3]);
						int newClass = Integer.parseInt(readData.split(":")[4]);
						int newNumber = Integer.parseInt(readData.split(":")[5]);
						
						java.sql.Statement st = null;
						st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
								ResultSet.CONCUR_READ_ONLY);
						
						st.executeQuery("UPDATE user SET Name='" + newName + "', School='" + newSchool
								+ "',Grade=" + newGrade + ",Class=" + newClass + ",Number=" + newNumber +
								"WHERE User='" + User + "'");
						
						st.close();
						  
					} catch (IOException e) {
						e.printStackTrace();
					} catch (SQLException ex) {
						Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
					}
			}


			if (readOPData == OP_EXIT) {// exit
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

	boolean login(String readData) {
		boolean toReturn = false;
		try {// 로그인시 DataInputStream 형식이 ID:PW 형식이다.
			System.out.println("ID:PW : " + readData);
			// 받은 정보를 서버에서 처리할 수 있도록 각각 분리하여 저장한다.
			String ID = readData.split(":")[0];
			String PW = readData.split(":")[1];

			String club_name;
			java.sql.Statement st = null;
			ResultSet rs = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

			if (st.execute("SELECT * FROM club where Name='" + ID + "'")) {// "ID"와 일치하는 데이터를 불러오기.
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

		if (student.equals("null")) {// 바코드데이터가 null일 때
			toReturn = User.OP_PURCHASE_RS_USERNULL;
		} else {
			try {
				if (simplifiedFDS(School_Type)) {// 축제시간일 때
					java.sql.Statement st = null;
					ResultSet rs = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);

					// 잔액조회
					if (st.execute("SELECT Balance FROM user where User='" + student + "'")) {
						rs = st.getResultSet();
					}
					// 잔액 추가 if문 입력

					while (rs.next()) {
						Balance = rs.getInt(1);
						System.out.println("Balance : " + Balance);
					}

					// 상품가격조회
					if (st.execute("SELECT Price FROM goods where Goods_Num='" + Goods_Num + "'")) {
						rs = st.getResultSet();
					}
					while (rs.next()) {
						Price = rs.getInt(1);
						System.out.println("Price : " + Price);
					}

					// 거래가능조건 잔액>=상품가
					// 결제기능 주석화로 제거 취소.
					if (Balance >= Price) {
						// 거래내역추가
						st.execute("INSERT INTO transactions (User, Club_Num, Goods_Num) VALUES ('" + student + "',"
								+ Club_Num + "," + Goods_Num + ")");

						// Balance차감
						st.execute("UPDATE user set Balance=(Balance-" + Price + ") where User='" + student + "'");

						// 동아리 수익증가
						st.execute("UPDATE club set Income=(Income+" + Price + ") where Club_Num='" + Club_Num + "'");

						//// 출석체크
						// 사기거래탐지시스템(FDS) 10분이내에 중복 출석체크 제한
						String LastCheckTime = null;
						java.util.Date LastCheckT = null;

						if (st.execute("SELECT Time FROM atd_history where User='" + student + "'")) {
							rs = st.getResultSet();
						}
						while (rs.next()) {
							// 마지막 출석체크 된 시간 저장 (같은 변수에 마지막이 될 때까지 넣기때문에 최종적으로는 마지막 출석시간이 저장된다.)
							LastCheckTime = rs.getString("Time");
						}

						// 마지막 출석체크 된 시간을 String to Date
						SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
								java.util.Locale.getDefault());
						try {
							LastCheckT = dateFormat.parse(LastCheckTime);
						} catch (ParseException e) {
							e.printStackTrace();
						}

						// LastCheckT에 +10분하기
						Calendar cal = Calendar.getInstance();
						cal.setTime(LastCheckT);
						cal.add(Calendar.MINUTE, 10);
						LastCheckT = cal.getTime();

						// 현재시간 측정
						long now = System.currentTimeMillis();
						java.util.Date currentTime = new java.util.Date(now);

						if (currentTime.compareTo(LastCheckT) > 0) {// 마지막 출석체크 된 시간 +10분 보다 현재시간이 더 클 때
							if (st.execute("SELECT Time FROM atd_history where User='" + student + "'and Club_Num ="
									+ Club_Num + "")) {// 이 동아리에서의 학생의 결제기록 불러오기
								rs = st.getResultSet();
							}

							boolean isChecked = false;

							while (rs.next()) {
								isChecked = true;
							}
							if (!isChecked) {// 이 동아리에서 이 학생이 출석체크 한적이 없을 때 (필요성 의문?)
								st.execute("INSERT INTO atd_history (User, Club_Num) VALUES ('" + student + "', "
										+ Club_Num + ")");
								st.execute("UPDATE user set Visits=(Visits+1) Where User='" + student + "'");
								st.execute("UPDATE club set Visits=(Visits+1) where Club_Num='" + Club_Num + "'");// 필요성
																													// 의문?
							} else {// 같은동아리에서 2회이상 출석체크 시도

							}
						} else {// 10분이내에 여러번 출석체크 시도

						}
						// 거래성공
						toReturn = User.OP_PURCHASE_RS_SUCCESS;
						// 거래X 였던 부분
					} else {
						// 거래실패 잔액부족
						toReturn = User.OP_PURCHASE_RS_OVERLIMIT;
					}
					st.close();
				} else {// 축제시간 이외에 요청
					toReturn = User.OP_PURCHASE_RS_NOTIME;
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

			if (!simplifiedFDS(School_Type)) {// 축제시간 이외일 때
				// 받은 정보를 서버에서 처리할 수 있도록 각각 분리하여 저장한다.
				String Goods_Name = readData.split(":")[0];
				int Price = Integer.parseInt(readData.split(":")[1]);

				java.sql.Statement st = null;
				st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
						ResultSet.CONCUR_READ_ONLY);
				st.execute("INSERT INTO goods (Club_Num, Goods_Name, Price) VALUES ('" + Club_Num + "','" + Goods_Name
						+ "','" + Price + "')");// 데이터베이스에 동아리고유번호, 상품명, 상품가로 데이터를 저장한다.
				st.close();
			}
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	String getBalanceVisits(String readData) {
		String toReturn = null;
		try {
			System.out.println("User : " + readData);

			java.sql.Statement st = null;
			ResultSet rs = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

			if (st.execute("SELECT * FROM user where User='" + readData + "'")) {// 바코드에 일치하는 학생데이터 불러오기
				rs = st.getResultSet();
			}
			// 팔찌미등록시 X
			if (!rs.next()) {// 불러온 학생데이터가 없을 때
				// User리스트에 추가
				rs.close();
				st.execute("INSERT INTO user (User) VALUES ('" + readData + "')");
				if (st.execute("SELECT * FROM user where User='" + readData + "'")) {
					rs = st.getResultSet();
				}
			}

			rs.beforeFirst();

			while (rs.next()) {
				String str = rs.getInt("Balance") + ":" + rs.getInt("Visits") + ":";

				switch (rs.getString("School")) {// 남고,여고의 출석체크기준이 다르기때문에 switch문 사용
				case "M":// 남고일 때
					if (rs.getInt("Club_Num") != 0 || rs.getInt("Visits") >= 3) {// 부원등록이 되어있거나, 부스이용횟수가 3회이상일 때
						str += "1";
					} else {
						str += "0";
					}
					break;
				case "W":// 여고일 때
					if (rs.getInt("Visits") >= 5) {// 부스이용횟수가 5회이상일 때
						str += "1";
					} else {
						str += "0";
					}
					break;
				default:
					str += "1";
					break;
				}
				System.out.println("Result : " + str);
				toReturn = str;

			}
			st.close();
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

			if (st.execute("SELECT * FROM goods where Club_Num='" + Club_Num + "'")) {// 동아리고유번호에 일치하는 상품들 불러오기
				rs = st.getResultSet();
			}
			while (rs.next()) {
				toReturn.add(rs.getInt("Goods_Num") + ":" + rs.getString("Goods_Name") + ":" + rs.getInt("Price"));// 상품고유번호,
																													// 상품명,
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
		ArrayList<String> toReturn = new ArrayList<>();
		try {

			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

			if (st.execute(
					"SELECT * FROM transactions where Club_Num='" + Club_Num + "' and User='" + readData + "'")) {
				rs = st.getResultSet();
			}

			while (rs.next()) {
				toReturn.add(rs.getString("Time") + ":" + rs.getString("Goods_Num"));
			}
			toReturn.add(User.OP_GET_GOODS_LIST_FIN);// 상품목록전송종료OP

			st.close();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
		return toReturn.toArray(new String[toReturn.size()]);
	}

	int refund(String readData) {
		int toReturn = 0;

		try {
			java.sql.Statement st = null;
			ResultSet rs = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			rs = st.executeQuery("SHOW DATABASES");

			String temp[] = { null, null, null };// 기취소여부:바코드:상품가

			// 이미취소된결제인지 확인
			if (st.execute("SELECT Cancel FROM transactions where Num='" + readData + "'")) {
				rs = st.getResultSet();
			}
			while (rs.next()) {
				temp[0] = rs.getString(1);// 현재 SQL버전이 NCHAR등을 지원하지 않아서 바꿈.
				System.out.println(temp[0]);
			}

			if (temp[0] == null) {
				// Num을통해 user바코드확인
				if (st.execute("SELECT User FROM transactions where Num='" + readData + "'")) {
					rs = st.getResultSet();
				}
				while (rs.next()) {
					temp[1] = rs.getString(1);
					System.out.println(temp[1]);
				}

				// Num을통해 가격확인
				if (st.execute("SELECT Price FROM transactions where Num='" + readData + "'")) {
					rs = st.getResultSet();
				}
				while (rs.next()) {
					temp[2] = rs.getString(1);
					System.out.println(temp[2]);
				}

				// 거래취소표시
				if (st.execute("UPDATE transactions set Cancel = '1' where Num='" + readData + "'"))
					;

				// Balance 복구
				if (st.execute("UPDATE user set Balance = (Balance-" + temp[2] + " where User='" + temp[1] + "'"))
					;

				st.close();
			} else {
				toReturn = User.OP_REFUND_RS_ALREADY_REFUNDED;// 이미 취소된 결제
			}
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
			toReturn = -1;
		}
		return toReturn;
	}

	void attendance(String readData) {
		try {
			java.sql.Statement st = null;
			st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			System.out.println("OP_ATD : " + readData);
			st.execute("UPDATE user set Club_Num='" + Club_Num + "' where User='" + readData + "'");// 바코드에
																									// 일치하는
																									// 학생에
																									// 동아리
																									// 고유번호저장하기
			st.close();
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	void edit_goods(String readData) {

		if (!simplifiedFDS(School_Type)) {// 축제시간 이외일 때
			try {
				int Goods_Num = Integer.parseInt(readData.split(":")[0]);
				String Goods_Name = readData.split(":")[1];
				int Price = Integer.parseInt(readData.split(":")[2]);

				java.sql.Statement st = null;
				st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
						ResultSet.CONCUR_READ_ONLY);

				System.out.println(readData);

				st.execute("UPDATE goods set Goods_Name='" + Goods_Name + "', Price='" + Price + "' where Goods_Num='"
						+ Goods_Num + "'");
				st.close();
			} catch (NumberFormatException e) {
				e.printStackTrace();
			} catch (SQLException ex) {
				Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	}

	void delete_goods(String readData) {
		try {
			System.out.println(readData);

			if (!simplifiedFDS(School_Type)) {// 축제시간 이외일 때
				java.sql.Statement st = null;
				st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
						ResultSet.CONCUR_READ_ONLY);

				st.execute("DELETE From goods where Goods_Num='" + readData + "'");
				st.close();
			}
		} catch (SQLException ex) {
			Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
		}
	}
}
