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
	public static final int port = 923;// ���� ��Ʈ�� �������־���.
	public static ArrayList<User> userList;// ���� Ŭ���̾�Ʈ ����̽��� �迭�� �����Ѵ�.
	public static Connection con = null;
	public static java.util.Date mTime, wTime;

	public static void main(String[] args) throws IOException {
		ServerSocket server;
		Socket newClient;
		User newUser;
		userList = new ArrayList<User>();
		server = new ServerSocket(port);

		try {// ���ŷ�Ž���ý���(FDS)���� �̿��� ����, ���� ���� ���۽ð��� DateŸ������ ��ȯ�Ͽ� �����Ѵ�.
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
			String strDate = "2018-09-07 09:00";
			java.util.Date wTime = dateFormat.parse(strDate);
			strDate = "2018-09-07 12:00";
			java.util.Date mTime = dateFormat.parse(strDate);
		} catch (ParseException e) {
			e.printStackTrace();
		}

		try {
			Class.forName("com.mysql.jdbc.Driver");
			con = DriverManager.getConnection("jdbc:mysql://localhost/cheongwonpaydb", "testuser", "testuserpassword");// DB����(MySQL)�ּҿ�
																														// ����,
																														// �н�����
			// DB����
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
			newUser.start();// ���� ���ӽ� ������ ArrayList�� �߰�
			userList.add(new User("unknown", newClient));
		}
	}
	// ���� ���� �� ���Ʈ ��
}

class User extends Thread {

	public static final int OP_LOGIN = 1, OP_PURCHASE = 2, OP_ADD_ITEM = 3, OP_RF_BAL = 4, OP_GET_GOODS_LIST = 5,
			OP_GET_REFUND_LIST = 6, OP_REFUND = 7, OP_ATD = 10, OP_EDIT_GOODS = 11, OP_DELETE_GOODS = 12, OP_EDIT_PW = 13,
			OP_GET_NAME = 14, OP_USER_MATCHING = 15, OP_BALANCE_CHARGE = 16, OP_PURCHASE_RS_NOTIME = 103, OP_PURCHASE_RS_OVERLIMIT = 104,
			OP_PURCHASE_RS_USERNULL = 106, OP_PURCHASE_RS_SUCCESS = 105, OP_CHARGE_RS_USERNULL = 107, OP_CHARGE_RS_SUCCESS = 108;// ��Ž� �̿��ϴ� ��� �ڵ�(OP-Code)�� ����
																							// ������Ÿ������ �����Ѵ�.
	public static final String OP_GET_GOODS_LIST_FIN = "##";// ��Ž� �̿��ϴ� ��� �ڵ�(OP-Code)�� ���� ������Ÿ������ �����Ѵ�. �� �ڵ常 ���������� ����ϴ�
															// ������ �Ʒ����� DataInputStream�� �� ���������� �ҷ����� �����̴�

	String userName;
	Socket socket;

	DataInputStream dis;
	DataOutputStream dos;

	public User(String s, Socket sc) {
		userName = s;
		socket = sc;
	}

	boolean simplifiedFDS(String School_Type) {// ���ŷ�Ž���ý���(FDS) �����ð��� ��� true, �����ð��̿��� ��� false�� ��ȯ.
		// ����ð� ����
		long now = System.currentTimeMillis();
		java.util.Date currentTime = new java.util.Date(now);

		// �����ð��� �ŷ����� Ȯ��
		switch (School_Type) {
		case "M":// ������ ��
			if (currentTime.compareTo(CheongwonPayServer.mTime) < 0) {
				return false;
			}
			break;
		case "W":// ������ ��
			if (currentTime.compareTo(CheongwonPayServer.wTime) < 0) {
				return false;
			}
			break;
		case "U":// ���յ��Ƹ��� ��
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
			this.stop();
		}

		String readData = null;// ���� �����͸� ���� ������Ÿ������ �����Ѵ�.
		int readOPData = 0;// ���� OP-Code�� ���� �迭�� �����Ѵ�.

		int Club_Num = 0;// ���Ƹ� ������ȣ�� ���� �迭�� �����Ѵ�.
		String School_Type = null;// �б�����(����� M, ����� W, ���յ��Ƹ��� U)�� ���� ������Ÿ������ �����Ѵ�.
		System.out.println("Socket Opened!");

		while (true) {
			try {
				readOPData = dis.readInt();
				System.out.println("readOPData : " + readOPData);
			} catch (IOException e) {
				e.printStackTrace();
				CheongwonPayServer.userList.remove(this);
				stop();
			}

			if (readOPData == OP_LOGIN) {// �α���
				try {// �α��ν� DataInputStream ������ ID:PW �����̴�.
					readData = dis.readUTF();// Ŭ���̾�Ʈ�� ���� ID, PW�� "readData"�� �����Ѵ�.
					System.out.println("ID:PW : " + readData);
					// ���� ������ �������� ó���� �� �ֵ��� ���� �и��Ͽ� �����Ѵ�.
					String ID = readData.split(":")[0];
					String PW = readData.split(":")[1];

					java.sql.Statement st = null;
					ResultSet rs = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);

					if (st.execute("SELECT * FROM club where Name='" + ID + "'")) {// "ID"�� ��ġ�ϴ� �����͸� �ҷ�����.
						rs = st.getResultSet();
					}

					String password = null, Club_Name = null;// "password"�� ���� �н�����, "Club_Name"�� ���Ƹ����� ���� ������Ÿ������ �����Ѵ�.

					while (rs.next()) {
						// ���� �н����� �ҷ�����
						password = rs.getString("PW");
						System.out.println("realPW : " + password);

						// ���Ƹ� �̸� �ҷ�����
						Club_Name = rs.getString("Name");
						System.out.println("Club_Name : " + Club_Name);

						// ���Ƹ�������ȣ����
						Club_Num = rs.getInt("Club_Num");
						System.out.println("Club_Num : " + Club_Num);

						// ���Ƹ� �Ҽ� �б����� ����
						School_Type = rs.getString("School");
						System.out.println("School_Type : " + Club_Name);
					}

					if (password != null && password.equals(PW)) {// �α��μ���������
						dos.writeUTF("Login Success!"); // DataOutputStream�� �Է� Ŭ���̾�Ʈ�� �ǵ��� ����
						System.out.println("Login Success!");
						// ���Ƹ� �̸� ����
						dos.writeUTF(Club_Name);

					} else {// �α��� ����
						dos.writeUTF("Login Failed!"); // DataOutputStream�� �Է� Ŭ���̾�Ʈ�� �ǵ��� ����
						System.out.println("Login Failed!");
						System.out.println("password : " + password);
						System.out.println("PW : " + PW);

					}

				} catch (IOException ex) {// �������� ��� �� ������ ������ �� �α� ���
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				} catch (SQLException sqex) {// SQL��� �� ������ ������ �� �α� ���
					System.out.println("SQLException: " + sqex.getMessage());
					System.out.println("SQLState: " + sqex.getSQLState());
				}
			}

			if (readOPData == OP_PURCHASE) {// �ŷ�, �⼮üũ
				try {// �ŷ�,�⼮üũ�� User:Goods_Num �����̴�.
					readData = dis.readUTF();// Ŭ���̾�Ʈ�� ���� �����͸� "readData"�� �����Ѵ�. User : Goods_num ����
					String User = readData.split(":")[0]; // ���ڵ� ������
					int Goods_Num = Integer.parseInt(readData.split(":")[1]);
					System.out.println("User : " + User);
					System.out.println("Goods_Num : " + Goods_Num);

					int Balance = 0, Price = 0;// "Balance"�� �ܾ�, "Price"�� ��ǰ���� ���� ������Ÿ������ �����Ѵ�.

					if (User.equals("null")) {// ���ڵ嵥���Ͱ� null�� ��
						dos.writeInt(OP_PURCHASE_RS_USERNULL);
					} else {
						if (simplifiedFDS(School_Type)) {// �����ð��� ��
							java.sql.Statement st = null;
							ResultSet rs = null;
							st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
									ResultSet.CONCUR_READ_ONLY);

							// �ܾ���ȸ
							if (st.execute("SELECT Balance FROM user where User='" + User + "'")) {
								rs = st.getResultSet();
							}
							// �ܾ� �߰� if�� �Է�

							while (rs.next()) {
								Balance = rs.getInt(1);
								System.out.println("Balance : " + Balance);
							}

							// ��ǰ������ȸ
							if (st.execute("SELECT Price FROM goods where Goods_Num='" + Goods_Num + "'")) {
								rs = st.getResultSet();
							}
							while (rs.next()) {
								Price = rs.getInt(1);
								System.out.println("Price : " + Price);
							}

							// �ŷ��������� �ܾ�>=��ǰ��
							// ������� �ּ�ȭ�� ���� ���.
							if (Balance >= Price) {
								// �ŷ������߰�
								st.execute("INSERT INTO transactions (User, Club_Num, Goods_Num) VALUES ('" + User
										+ "'," + Club_Num + "," + Goods_Num + ")");

								// Balance����
								st.execute("UPDATE user set Balance=(Balance-" + Price + ") where User='" + User + "'");

								// ���Ƹ� ��������
								st.execute("UPDATE club set Income=(Income+" + Price + ") where Club_Num='" + Club_Num
										+ "'");

								//// �⼮üũ
								// ���ŷ�Ž���ý���(FDS) 10���̳��� �ߺ� �⼮üũ ����
								String LastCheckTime = null;
								java.util.Date LastCheckT = null;

								if (st.execute("SELECT Time FROM atd_history where User='" + User + "'")) {
									rs = st.getResultSet();
								}
								while (rs.next()) {
									// ������ �⼮üũ �� �ð� ���� (���� ������ �������� �� ������ �ֱ⶧���� ���������δ� ������ �⼮�ð��� ����ȴ�.)
									LastCheckTime = rs.getString("Time");
								}

								// ������ �⼮üũ �� �ð��� String to Date
								SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
										java.util.Locale.getDefault());
								try {
									LastCheckT = dateFormat.parse(LastCheckTime);
								} catch (ParseException e) {
									e.printStackTrace();
								}

								// LastCheckT�� +10���ϱ�
								Calendar cal = Calendar.getInstance();
								cal.setTime(LastCheckT);
								cal.add(Calendar.MINUTE, 10);
								LastCheckT = cal.getTime();

								// ����ð� ����
								long now = System.currentTimeMillis();
								java.util.Date currentTime = new java.util.Date(now);

								if (currentTime.compareTo(LastCheckT) > 0) {// ������ �⼮üũ �� �ð� +10�� ���� ����ð��� �� Ŭ ��
									if (st.execute("SELECT Time FROM atd_history where User='" + User
											+ "'and Club_Num =" + Club_Num + "")) {// �� ���Ƹ������� �л��� ������� �ҷ�����
										rs = st.getResultSet();
									}

									boolean isChecked = false;

									while (rs.next()) {
										isChecked = true;
									}
									if (!isChecked) {// �� ���Ƹ����� �� �л��� �⼮üũ ������ ���� �� (�ʿ伺 �ǹ�?)
										st.execute("INSERT INTO atd_history (User, Club_Num) VALUES ('" + User + "', "
												+ Club_Num + ")");
										st.execute("UPDATE user set Visits=(Visits+1) Where User='" + User + "'");
										st.execute(
												"UPDATE club set Visits=(Visits+1) where Club_Num='" + Club_Num + "'");// �ʿ伺
																														// �ǹ�?
									} else {// �������Ƹ����� 2ȸ�̻� �⼮üũ �õ�

									}
								} else {// 10���̳��� ������ �⼮üũ �õ�

								}
								// �ŷ�����
								dos.writeInt(OP_PURCHASE_RS_SUCCESS);
								// �ŷ�X ���� �κ�
							} else {
								// �ŷ����� �ܾ׺���
								dos.writeInt(OP_PURCHASE_RS_OVERLIMIT);
							}
						} else {// �����ð� �̿ܿ� ��û
							dos.writeInt(OP_PURCHASE_RS_NOTIME);
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			if (readOPData == OP_ADD_ITEM) {// ��ǰ�߰�
				try {
					readData = dis.readUTF();// Ŭ���̾�Ʈ�� ���� ��ǰ��, ��ǰ���� "readData"�� �����Ѵ�.
					System.out.println("Goods_Name, Price : " + readData);

					if (!simplifiedFDS(School_Type)) {// �����ð� �̿��� ��
						// ���� ������ �������� ó���� �� �ֵ��� ���� �и��Ͽ� �����Ѵ�.
						String Goods_Name = readData.split(":")[0];
						int Price = Integer.parseInt(readData.split(":")[1]);

						java.sql.Statement st = null;
						st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
								ResultSet.CONCUR_READ_ONLY);
						st.execute("INSERT INTO goods (Club_Num, Goods_Name, Price) VALUES ('" + Club_Num + "','"
								+ Goods_Name + "','" + Price + "')");// �����ͺ��̽��� ���Ƹ�������ȣ, ��ǰ��, ��ǰ���� �����͸� �����Ѵ�.
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			if (readOPData == OP_RF_BAL) {// �ܾ�, �⼮�� ��ȸ + User����Ʈ�� �������(����)�߰�
				try {
					String User = dis.readUTF();// Ŭ���̾�Ʈ�� ���� ���ڵ带 "readData"�� �����Ѵ�.
					System.out.println("User : " + readData);

					java.sql.Statement st = null;
					ResultSet rs = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);

					if (st.execute("SELECT * FROM user where User='" + User + "'")) {// ���ڵ忡 ��ġ�ϴ� �л������� �ҷ�����
						rs = st.getResultSet();
					}
					// ����̵�Ͻ� X
					if (!rs.next()) {// �ҷ��� �л������Ͱ� ���� ��
						// User����Ʈ�� �߰�
						st.execute("INSERT INTO user (User) VALUES ('" + User + "')");
						if (st.execute("SELECT * FROM user where User='" + User + "'")) {
							rs = st.getResultSet();
						}
					}
					rs.beforeFirst();

					while (rs.next()) {
						String str = rs.getInt("Balance") + ":" + rs.getInt("Visits") + ":";

						switch (rs.getString("School")) {// ����,������ �⼮üũ������ �ٸ��⶧���� switch�� ���
						case "M":// ������ ��
							if (rs.getInt("Club_Num") != 0 || rs.getInt("Visits") >= 3) {// �ο������ �Ǿ��ְų�, �ν��̿�Ƚ���� 3ȸ�̻��� ��
								str += "1";
							} else {
								str += "0";
							}
							break;
						case "W":// ������ ��
							if (rs.getInt("Visits") >= 5) {// �ν��̿�Ƚ���� 5ȸ�̻��� ��
								str += "1";
							} else {
								str += "0";
							}
							break;
						}
						System.out.println("Result : " + str);
						dos.writeUTF(str);
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			if (readOPData == OP_GET_GOODS_LIST) {// ��ǰ��Ϻҷ����� goodsnum,name,price�ֱ�
				try {
					java.sql.Statement st = null;
					ResultSet rs = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);

					if (st.execute("SELECT * FROM goods where Club_Num='" + Club_Num + "'")) {// ���Ƹ�������ȣ�� ��ġ�ϴ� ��ǰ�� �ҷ�����
						rs = st.getResultSet();
					}
					while (rs.next()) {
						dos.writeUTF(
								rs.getInt("Goods_Num") + ":" + rs.getString("Goods_Name") + ":" + rs.getInt("Price"));// ��ǰ������ȣ,
																														// ��ǰ��,
																														// ��ǰ����
																														// ����
					}
					dos.writeUTF(OP_GET_GOODS_LIST_FIN);// ��ǰ��� ��������OP����
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			if (readOPData == OP_GET_REFUND_LIST) {// get����ڰ������ for refund time,goodsnum,name,price�ֱ�
				try {
					String User = dis.readUTF();// ���ڵ�

					java.sql.Statement st = null;
					ResultSet rs = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);

					if (st.execute(
							"SELECT * FROM transactions where Club_Num='" + Club_Num + "' and User='" + User + "'")) {
						rs = st.getResultSet();
					}

					while (rs.next()) {
						dos.writeUTF(rs.getString("Time") + ":" + rs.getString("Goods_Num"));
					}
					dos.writeUTF(OP_GET_GOODS_LIST_FIN);// ��ǰ�����������OP
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}

			}
			if (readOPData == OP_REFUND) {// ��ü��� (+������)
				try {
					// �̰� �ѹ�������
					readData = dis.readUTF();// �ŷ� ��ȣ

					java.sql.Statement st = null;
					ResultSet rs = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);
					rs = st.executeQuery("SHOW DATABASES");

					String temp[] = null;// ����ҿ���:���ڵ�:��ǰ��

					// �̹���ҵȰ������� Ȯ��
					if (st.execute("SELECT Cancel FROM transactions where Num='" + readData + "'")) {
						rs = st.getResultSet();
					}
					while (rs.next()) {
						temp[0] = rs.getNString(1);
						System.out.println(temp[0]);
					}

					if (temp[0].length() == 0) {
						// Num������ user���ڵ�Ȯ��
						if (st.execute("SELECT User FROM transactions where Num='" + readData + "'")) {
							rs = st.getResultSet();
						}
						while (rs.next()) {
							temp[1] = rs.getNString(1);
							System.out.println(temp[1]);
						}

						// Num������ ����Ȯ��
						if (st.execute("SELECT Price FROM transactions where Num='" + readData + "'")) {
							rs = st.getResultSet();
						}
						while (rs.next()) {
							temp[2] = rs.getNString(1);
							System.out.println(temp[2]);
						}

						// �ŷ����ǥ��
						if (st.execute("UPDATE transactions set Cancel = '1' where Num='" + readData + "'"))
							;

						// Balance ����
						if (st.execute(
								"UPDATE user set Balance = (Balance-" + temp[2] + " where User='" + temp[1] + "'"))
							;
					} else {
						dos.writeInt(101);// �̹� ��ҵ� ����
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			if (readOPData == OP_ATD) {// �⼮üũ �ο����
				try {
					readData = dis.readUTF();// Ŭ���̾�Ʈ�� ���� ���ڵ������� "readData"�� �����Ѵ�.
					java.sql.Statement st = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);
					System.out.println("OP_ATD : " + readData);
					st.execute("UPDATE user set Club_Num='" + Club_Num + "' where User='" + readData + "'");// ���ڵ忡 ��ġ�ϴ�
																											// �л��� ���Ƹ�
																											// ������ȣ�����ϱ�
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			if (readOPData == OP_EDIT_GOODS) {// ��ǰ��Ϻ���
				try {
					readData = dis.readUTF();// Ŭ���̾�Ʈ�� ���� ��ǰ������ȣ, ��ǰ��, ������ "readData"�� �����Ѵ�.

					if (!simplifiedFDS(School_Type)) {// �����ð� �̿��� ��
						int Goods_Num = Integer.parseInt(readData.split(":")[0]);
						String Goods_Name = readData.split(":")[1];
						int Price = Integer.parseInt(readData.split(":")[2]);

						java.sql.Statement st = null;
						st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
								ResultSet.CONCUR_READ_ONLY);

						System.out.println(readData);

						st.execute("UPDATE goods set Goods_Name='" + Goods_Name + "', Price='" + Price
								+ "' where Goods_Num='" + Goods_Num + "'");
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			if (readOPData == OP_DELETE_GOODS) {// ��ǰ����
				try {
					readData = dis.readUTF();// Ŭ���̾�Ʈ�� ���� ��ǰ������ȣ�� "readData"�� �����Ѵ�.
					System.out.println(readData);

					if (!simplifiedFDS(School_Type)) {// �����ð� �̿��� ��
						java.sql.Statement st = null;
						st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
								ResultSet.CONCUR_READ_ONLY);

						st.execute("DELETE From goods where Goods_Num='" + readData + "'");
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			if (readOPData == OP_EDIT_PW) {// �н����� ����
				try {
					readData = dis.readUTF();// Ŭ���̾�Ʈ�� ���� ������ �н����带 "readData"�� �����Ѵ�.
					System.out.println("after pw: " + readData);

					java.sql.Statement st = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);

					st.execute("UPDATE club set PW='" + readData + "' where Club_Num='" + Club_Num + "'");// �н����带 �����Ѵ�.
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			if (readOPData == OP_GET_NAME) {// �̸���ȸ
				try {
					String User = dis.readUTF();// Ŭ���̾�Ʈ�� ���� ���ڵ��� "readData"�� �����Ѵ�.
					System.out.println("User : " + readData);

					java.sql.Statement st = null;
					ResultSet rs = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);

					if (st.execute("SELECT Name FROM user where User='" + User + "'")) {// ���ڵ忡 ��ġ�ϴ� �л��� �ҷ�����
						rs = st.getResultSet();
					}

					while (rs.next()) {
						String str = rs.getNString(1);
						if (str != null) {// null�� �ƴ� ��
							dos.writeUTF(str);// �̸����� �����Ѵ�.
						} else {
							dos.writeUTF("lookup error!");// "lookup error!"�̶�� �����Ѵ�.
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			if (readOPData == OP_USER_MATCHING) {// ������Ī
				try {
					readData = dis.readUTF();// Ŭ���̾�Ʈ�� ���� ���ڵ�, �й�, �б����� ���� "readData"�� �����Ѵ�.
					// ���� ������ �������� ó���� �� �ֵ��� ���� �и��Ͽ� �����Ѵ�.
					String Barcode = readData.split(":")[0];
					String Student_ID = readData.split(":")[1];
					String Type = readData.split(":")[2];
					String Grade = Student_ID.substring(0, 1);
					String Class = Student_ID.substring(1, 3);
					String Number = Student_ID.substring(3);
					System.out.println("User : " + Barcode);
					System.out.println("Student_ID : " + Grade + ":" + Class + ":" + Number);

					java.sql.Statement st = null;
					ResultSet rs = null;
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);

					switch (Type) {
					case "1":// ������ ��
						st.execute("UPDATE user set User='" + Barcode + "' where Grade='" + Grade + "' and Class='"
								+ Class + "' and Number='" + Number + "' and School='M'");
						break;
					case "2":// ������ ��
						st.execute("UPDATE user set User='" + Barcode + "' where Grade='" + Grade + "' and Class='"
								+ Class + "' and Number='" + Number + "' and School='W'");
						break;
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			if (readOPData == OP_BALANCE_CHARGE) {
				// �ܾ� ������ �ڵ�
				// user:wtbalance�������� dis;
				try {
					readData = dis.readUTF();

					String User = readData.split(":")[0];
					String wtbalance = readData.split(":")[1];

					java.sql.Statement st = null;
					//ResultSet rs = null; �޾ƿ� �ʿ䰡 ������ ResultSet�� �ʿ� ��������? �� �𸣰���. 
					st = CheongwonPayServer.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_READ_ONLY);
					
					System.out.println("User : " + User);
					System.out.println("Balance : " + wtbalance);
					
					if (User.equals("null")) {// ���ڵ嵥���Ͱ� null�� ��
						dos.writeInt(OP_CHARGE_RS_USERNULL);
					} else {
						st.execute("UPDATE user set Balance=(Balance+" + wtbalance + ") where User='" + User + "'");
						dos.writeInt(OP_CHARGE_RS_SUCCESS);
					}
					
				} catch (IOException e) {
					e.printStackTrace();
				} catch (SQLException ex) {
					Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			if (readOPData == 1110) {// exit
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
	}
}