import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.*;
import java.io.*;

public class PhoneExecutor{

	
	
	private final int port = 5999;
	
	private ObjectOutputStream OS;
	private ObjectInputStream IS;
	private Socket client;
	
	private String deviceName;
	private String key;
	
	private ArrayList<String> names, numbers;
	
	private ArrayList<MessageThread> messageThreads;
	private final MainModule phoneModule;

	private volatile boolean flag;
	
	public PhoneExecutor(String devName, String key) throws Exception{
		deviceName = devName;
		client = new Socket(devName, port);
		OS = new ObjectOutputStream(client.getOutputStream());
		IS = new ObjectInputStream(client.getInputStream());
		
		OS.writeObject(key);
		if(((String)IS.readObject()).equals(".err")) throw new RuntimeException("Invlaid Credential");
		else{
			try{
			names = (ArrayList<String>)IS.readObject();
			numbers = (ArrayList<String>)IS.readObject();
			scrubContacts();
			formatNumbers();
			alpha();
			for(int i = 0; i < names.size(); i++){
				System.out.println(names.get(i) + ": " + numbers.get(i));
			}
			initMessageThreads();
			phoneModule = new MainModule();
			(new Thread(new MessageHandler())).start();
			}catch(ClassCastException e){
				throw new IOException("Disconnect Button hit on the phone");
			}
		}
	}
	
	
	private void alpha(){
		ArrayList<String> alphanames = new ArrayList<String>(names);
		ArrayList<String> alphanumbers = new ArrayList<String>();
		Collections.sort(alphanames, new Comparator<String>(){

			@Override
			public int compare(String o1, String o2) {
				return o1.compareToIgnoreCase(o2);
			}
			
		});
		for(String name : alphanames){
			int id = names.indexOf(name);
			alphanumbers.add(numbers.get(id));
		}
		
		
		
		names = alphanames;
		numbers = alphanumbers;
	}
	
	private void scrubContacts(){
		
		for(int i = 0; i < names.size();){
			if(numbers.get(i) == null){
				numbers.remove(i);
				names.remove(i);
			}else{
				i++;
			}
		}
	}
	
	private void formatNumbers(){
		for(int i = 0; i < numbers.size(); i++){
			String number = numbers.get(i);
			for(int j = 0; j < number.length(); j++){
				if(!Character.isDigit(number.charAt(j))){
					number = number.replace(number.charAt(j), ' ');
				}
			}
			number = number.replaceAll("\\s", "");
			numbers.set(i, number);
			
		}
	}
	
	private void initMessageThreads(){
		messageThreads = new ArrayList<MessageThread>();
		for(String str : names){
			messageThreads.add(new MessageThread(str));
		}
	}
	
	private class MainModule extends JFrame{
		private static final int WIDTH = 300;
		private static final int HEIGHT = 600;
		private JPanel contactListPanel = new JPanel();
		private ArrayList<JButton> contactButtons;
		private JScrollPane contactScroll;
		
		MainModule(){
			setSize(WIDTH, HEIGHT);
			setTitle("NetSMS: " + deviceName);
			setDefaultCloseOperation(EXIT_ON_CLOSE);
			setResizable(false);
			createLayout();
			setVisible(true);
		}
		
	
		
		private void createLayout(){
			getContentPane().setLayout(new BorderLayout());
			contactListPanel.setLayout(new BoxLayout(contactListPanel, BoxLayout.Y_AXIS)); 
//		    contactListPanel.setMinimumSize(new Dimension(WIDTH, HEIGHT));
//		    contactListPanel.setPreferredSize(new Dimension(WIDTH, HEIGHT));
//		    contactListPanel.setMaximumSize(new Dimension(WIDTH, HEIGHT));
			
			contactButtons = new ArrayList<JButton>();
			for(String str : names){
				contactButtons.add(new JButton(str));
			}
			
			
			
			int i = 0;
			for(JButton j : contactButtons){
				j.addActionListener(new ButtonAction());
				j.setBorder(BorderFactory.createEmptyBorder());
				j.setContentAreaFilled(false);
				j.setAlignmentX(Component.CENTER_ALIGNMENT);
				j.setForeground(Color.WHITE);
				j.setBackground(Color.BLACK);
				j.setToolTipText(numbers.get(contactButtons.indexOf(j)));
				contactListPanel.add(j, i);
				contactListPanel.add(new JSeparator(SwingConstants.HORIZONTAL), i + 1);
				i+=2;
			}
			contactScroll = new JScrollPane(contactListPanel);
			contactScroll.getVerticalScrollBar().setUnitIncrement(15);
			contactListPanel.setBackground(Color.BLACK);
			getContentPane().add(contactScroll, BorderLayout.CENTER);
			JButton j = new JButton("Compose");
			j.setMaximumSize(new Dimension(WIDTH, 70));
			j.addActionListener(new ActionListener(){

				@Override
				public void actionPerformed(ActionEvent arg0) {
					String recipient = JOptionPane.showInputDialog("Enter name or number of recipient");
					if(recipient == null) return;
					int iD;
					if(Character.isDigit(recipient.charAt(0))){
						iD = getID(recipient);
						if(iD == -1){
							names.add(recipient);
							numbers.add(recipient);
							contactButtons.add(new JButton(recipient));
							contactButtons.get(contactButtons.size() -1).addActionListener(new ButtonAction());
							contactListPanel.add(new JSeparator(SwingConstants.HORIZONTAL), 2);
							messageThreads.add(new MessageThread(recipient));
							messageThreads.get(messageThreads.size() -1).setVisible(true);
						}else{
							messageThreads.get(iD).setVisible(true);
						}
					}else{
						iD = -1;
						for(int i = 0 ; i < names.size(); i++){
							if(names.get(i).equalsIgnoreCase(recipient)){
								iD = i;
								break;
							}
						}if(iD != -1){
							messageThreads.get(iD).setVisible(true);
						}
					}
					
				}
				
			});
			getContentPane().add(j, BorderLayout.PAGE_END);
		}
		
		private class ButtonAction implements ActionListener{

			@Override
			public void actionPerformed(ActionEvent arg0) {
				JButton src = (JButton)arg0.getSource();
				System.out.println(src.getText());
				int iD = contactButtons.indexOf(src);
				System.out.println(iD);
				MessageThread m = messageThreads.get(iD);
				System.out.println(m.getTitle());
				System.out.println(m.isVisible());
				if(!m.isVisible()) m.setVisible(true);
				else m.setVisible(false);
				System.out.println(m.isVisible());
			}
			
		}
		
	}
	
	private class MessageThread extends JFrame{
		private final int WIDTH = 400;
		private final int HEIGHT = 300;
		
		private JTextArea messageArea;
		private JTextField sendArea;
		private JScrollPane messageScroll;
		private JButton sendButton = new JButton("Send");
		private JPanel sendPane;
		private GridLayout mainLayout, sendLayout;
		
		int iD;
		private String senderName, number;
		
		
		MessageThread(String name){
			senderName = name;
			setTitle(name);
			setSize(WIDTH, HEIGHT);
			setDefaultCloseOperation(HIDE_ON_CLOSE);
			iD = names.indexOf(name);
			number = numbers.get(iD);
			mainLayout = new GridLayout(2,1);
			mainLayout.setVgap(20);
			sendLayout = new GridLayout(2,2);
			sendArea = new JTextField();
			sendPane = new JPanel();
			sendPane.setLayout(sendLayout);
			sendPane.add(sendArea);
			sendPane.add(sendButton, 1);
			sendButton.addActionListener(new SendAction());
			messageArea = new JTextArea();
			messageArea.setEditable(false);
			messageScroll = new JScrollPane(messageArea);
			getRootPane().setDefaultButton(sendButton);
			UIManager.put("Button.defaultButtonFollowsFocus", Boolean.TRUE);
			messageScroll.setBorder(new EmptyBorder(10,10,0,10));
			sendPane.setBorder(new EmptyBorder(10,10,10,10));
			messageArea.setBackground(new Color(0,10,10));
			messageArea.setForeground(new Color(0,255,255));
			messageScroll.setBackground(new Color(0,0,0,0));
			messageArea.setLineWrap(true);
			messageArea.setWrapStyleWord(true);
			sendArea.setBackground(new Color(0,0,0));
			sendArea.setForeground(new Color(0,255,255));
			sendPane.setBackground(new Color(0,0,0,0));
			messageArea.setRequestFocusEnabled(true);
			getContentPane().setBackground(new Color(0, 0, 0));
			getContentPane().setLayout(mainLayout);
			getContentPane().add(messageScroll);
			getContentPane().add(sendPane);
		}
		
		private class SendAction implements ActionListener{

			@Override
			public void actionPerformed(ActionEvent e) {
				String msg = sendArea.getText();
				String[] message = {msg, number};
				try {
					OS.writeObject(message);
					messageArea.append("You said: " + msg + "\n");
					messageArea.scrollRectToVisible(new Rectangle(0,messageArea.getHeight() - 2,1,1));
					sendArea.setText("");
				} catch (IOException ex) {
					// TODO Auto-generated catch block
					System.out.println(ex.getMessage());
				}
				
			}
			
		}
		
		void displayMessage(String msg){
			if(!this.isVisible()) this.setVisible(true);
			messageArea.append(senderName + ": " + msg + "\n");
			messageArea.scrollRectToVisible(new Rectangle(0,messageArea.getHeight() - 2,1,1));
		}
		
	}
	
	private int getID(String number){
		
		for(int i = 0; i < numbers.size(); i++){
			if(numbers.get(i).contains(number)){
				return i;
				
			}
		}
		return -1;
	}
	
	private class MessageHandler implements Runnable{

		private void distributeMessage(String[] message){
			try{
				int iD = getID(message[1]);
				if(iD == -1){
					names.add(message[1]);
					numbers.add(message[1]);
					messageThreads.add(new MessageThread(message[1]));
					iD = messageThreads.size() - 1;
				}
				messageThreads.get(iD).displayMessage(message[0]);
			}catch(ArrayIndexOutOfBoundsException e){
				throw new IllegalArgumentException("Insufficient array length");
			}
		}
		
		@Override
		public void run() {
			while(true){
				Object objectReceived = null;
				try{
					objectReceived = IS.readObject();
					String[] message = (String[])objectReceived;
					if(message.length >= 2){
						distributeMessage(message);
					}
				}catch(ClassCastException e){
					String cmd = (String)objectReceived;
					if(cmd.equalsIgnoreCase(".stop")){
						flag = false;
						break;	
					}
					
					
				}catch(EOFException e){
					JOptionPane.showMessageDialog(null, "The device has terminated the connection. NetSMS will now exit!");
					System.exit(0);
				}catch(SocketException e){
					JOptionPane.showMessageDialog(null, "The device has terminated the connection. NetSMS will now exit!");
					System.exit(0);
				}catch(Exception e){
					System.out.println(e);
				}
			}
			
		}
		
	}
	
	
	public static void main(String[] args){
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null, "Fatal Error Occurred!");
			System.exit(1);
		}
		
		PhoneExecutor phone = null;
		boolean flag;
		do{
			flag = false;
			String devName = JOptionPane.showInputDialog("Enter device Name: ");
			if(devName == null) break;
			String key = JOptionPane.showInputDialog("Enter key");
			if(key == null) break;
			try{
				phone = new PhoneExecutor(devName, key);
			}catch(Exception e){
				e.printStackTrace();
				flag = true;
			}
		}while(flag);
		if(phone == null)
		JOptionPane.showMessageDialog(null, "Bye-Bye!");
		
		//System.out.println(3 / 0);
	}
	
}
