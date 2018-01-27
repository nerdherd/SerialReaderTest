package org.usfirst.frc.team687.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.usfirst.frc.team687.robot.RobotMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import edu.wpi.cscore.UsbCamera;
import edu.wpi.cscore.VideoMode.PixelFormat;
import edu.wpi.first.networktables.EntryListenerFlags;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTableValue;
import edu.wpi.first.networktables.TableEntryListener;
import edu.wpi.first.wpilibj.CameraServer;
import edu.wpi.first.wpilibj.SerialPort;
import edu.wpi.first.wpilibj.SerialPort.Port;
import edu.wpi.first.wpilibj.command.Subsystem;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class Jevois extends Subsystem implements Runnable {

	public static boolean commandStatus = false;
	
	private String			command;
	
	private SerialPort 		serialPort;
	private UsbCamera 		cam; //MJPG
	
	private Thread 			listener;
	private AtomicBoolean	keepRunning;
	private AtomicBoolean	cmdReady;
	private AtomicBoolean	cmdPending;
	private AtomicBoolean	cmdLock;
	private AtomicReference<String> cmdToSend;
	private StringBuffer	cmdResp, buf;
	private NetworkTable	ntJevois, ntJevoisCamCtrls, ntJevoisVisionParams, ntJevoisTarget;
	private JsonParser		jsonParser;
	
	SerialPort.Port port = Port.kUSB1;
	String cameraDev = "/dev/video0";
	
	private final String[]  CAMERA_CONTROLS = {"brightness\n","contrast\n","saturation\n","autowb\n","dowb\n","redbal\n","bluebal\n","autogain\n",
												"gain\n","hflip\n","vflip\n","sharpness\n","autoexp\n","absexp\n","presetwb\n"};
	
	private TableEntryListener ntListener = new TableEntryListener() {
		StringBuffer sb = new StringBuffer();
		StringBuffer tmp = new StringBuffer();
		
		@Override
		public void valueChanged(NetworkTable table, String key, NetworkTableEntry entry, NetworkTableValue newValue, int flags) {
			
			System.out.println(table.getPath() + " " + key);
			sb.setLength(0);
			tmp.setLength(0);
			
			// check where the update is; if in CameraControls we will either issue a savecamctrls command to have the
			// jevois write current control settings to a script file, or issue a setcam/storcam commands to have the
			// new value take effect on the jevois
			if (table.getPath().contains("CameraControls")) {
				if (key.equalsIgnoreCase("save")) {	
					if (newValue.getBoolean()) {
						System.out.println(sendCommand("savecamctrls\n"));
						entry.setBoolean(false);		// clear the save flag once we perform the save
					}
				} else {	// we're updating a camera control, send it to the jevois to take effect
					tmp.append(key).append(" ").append(newValue.getString()).append("\n");
					sb.append("setcam ").append(tmp);		// setcam activates the new setting
					System.out.println(sendCommand(sb.toString()));
					sb.setLength(0);
					sb.append("storcam ").append(tmp);		// storcam remembers the setting in a module variable so it can be saved
					System.out.println(sendCommand(sb.toString()));
				}
			} 
			
			// if we're updating VisionParams we will either issue a savevisionparams command to have the jevois
			// serialize current settings to a file, or issue a setvisionparam command to have the new value
			// take effect in the pipeline
			if (table.getPath().contains("VisionParams")) {	
				if (key.equalsIgnoreCase("save")) {
					if (newValue.getBoolean()) {
						System.out.println(sendCommand("savevisionparams\n"));
						entry.setBoolean(false);
					}
				} else {	// send the new vision param value to the jevois so it can take effect in the pipeline
					sb.append("setvisionparam ").append(key).append(" ").append(newValue.getString()).append("\n");
					System.out.println(sendCommand(sb.toString()));
				}
			}
			
		}
	};
	
	@Override
	protected void initDefaultCommand() {
		try {
			init(port, cameraDev);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		sendCommand("ping\n");
	}
	
	private void init(SerialPort.Port port, String cameraDev) throws Exception {
		try {
			serialPort = new SerialPort(115200, port);
		} catch (Exception e) {
			throw new Exception("jevoisLib: Failed to create Jevois SerialPort: " + port, e);
		}
		cam = CameraServer.getInstance().startAutomaticCapture("JeVois Camera", cameraDev);
		keepRunning = new AtomicBoolean(true);
		cmdReady = new AtomicBoolean(false);
		cmdPending = new AtomicBoolean(false);
		cmdLock = new AtomicBoolean(false);
		cmdToSend = new AtomicReference<String>();
		cmdToSend.set("");
		cmdResp = new StringBuffer();
		buf = new StringBuffer();
		ntJevois = NetworkTableInstance.getDefault().getTable("jevois");
		ntJevoisCamCtrls = ntJevois.getSubTable("CameraControls");
		ntJevoisVisionParams = ntJevois.getSubTable("VisionParams");
		ntJevoisTarget = ntJevois.getSubTable("Target");
		ntJevoisCamCtrls.getEntry("save").setBoolean(false);
		ntJevoisCamCtrls.addEntryListener(ntListener, EntryListenerFlags.kLocal | EntryListenerFlags.kUpdate);
		ntJevoisVisionParams.addEntryListener(ntListener, EntryListenerFlags.kLocal | EntryListenerFlags.kUpdate);
		jsonParser = new JsonParser();
		listener = new Thread(this);
		listener.start();
		setCameraControls();
		readVisionParams();
		publishCameraControls();
//		setVideoMode(PixelFormat.kYUYV, 320, 254, 60);
	}
	
	private void setVideoMode(PixelFormat f, int w, int h, int fps) {
		cam.setVideoMode(f, w, h, fps);
	}
	
	protected void parseTargetInfo(String str) {
		// str should now hold a complete JSON string to parse and publish to network tables
		try {
			JsonObject obj = (JsonObject) jsonParser.parse(str);
			for (String key : obj.keySet()) {
				ntJevoisTarget.getEntry(key).setDouble(obj.get(key).getAsDouble());
			}
		} catch (Exception e) {
			System.out.println("jevoisLib: error " + e.getMessage() + " during parseTargetInfo(" + str + ")");
		}
	}
	
	public String getInfo() {
		return sendCommand("info\n");
	}
	
	private void setCameraControls() {
		sendCommand("runscript /jevois/data/camControls.cfg\n");
	}
	
	// send jevois command which reads saved vision params from file and returns them as a json string
	// parse the returned json string and publish the params to NT so tuner can update
	private void readVisionParams() {
		String[] lines = sendCommand("readvisionparams\n").split("\n");
		for (int i=0; i<lines.length - 1; i++) {
			try {
				JsonObject obj = (JsonObject) jsonParser.parse(lines[i]);
				for (String key : obj.keySet()) {
					ntJevoisVisionParams.getEntry(key).setDouble(obj.get(key).getAsDouble());
				}
			} catch (Exception e) {
				System.out.println("jevoisLib: error " + e.getMessage() + " during readVisionParams()");
			}
		}
	}
	
	private void publishCameraControls() {
		try {
			for (String cmd : CAMERA_CONTROLS) {
				buf.setLength(0);
				buf.append("getcam ").append(cmd);
				String[] resp = sendCommand(buf.toString()).split("\n");
				if (resp[1].equalsIgnoreCase("OK")) {
					//String[] pair = resp.substring(0, resp.indexOf("OK")).split(" ");
					String[] pair = resp[0].split(" ");
					ntJevoisCamCtrls.getEntry(pair[0].trim()).setString(pair[1].trim());
				} else {
					System.out.println(buf.append(" returned NOT OK").toString());
				}
			}
		} catch (Exception e) {
			System.out.println("jevoisLib: publishCameraControls() error - " + e.getMessage()); 		
		}
	}
	
	public String streamTargetInfo(boolean streamOn) {
		return sendCommand("sendtargets " + ((streamOn) ? "on\n" : "off\n"));
	}
		 
	public String ping(){
	    String response = sendCommand("ping\n");
	    return response;
	}
	
	// send command and return response
	private String sendCommand(String c) {
		//System.out.println("checking cmdLock....");
		while (!cmdLock.compareAndSet(false, true)) {
			try { Thread.sleep(2); } catch (Exception e) {}
		}
		
		//System.out.println("cmdLock OK....");
		cmdToSend.set(c);
		cmdReady.compareAndSet(false, true);
			
		//System.out.println("waiting resp....");
		do {
			try { Thread.sleep(5); } catch (Exception e) {}
		} while (cmdPending.get() || cmdReady.get());
		
		//System.out.println("rep OK....");
		String ret = cmdResp.toString();
		cmdLock.set(false);
		return ret;
	}
	
	public void commandInterface() {
		command = SmartDashboard.getString("command: ", command);
    	if (commandStatus) {
    		sendCommand(command);
    		SmartDashboard.putString("command: ", command);
    		commandStatus = false;
    	}
	}
	
	@Override
	public void run() {
		
		while (keepRunning.get() && !Thread.interrupted()) {
			// there's a serial command waiting to be sent, so send it
			if (cmdReady.get()) {
				System.out.print("jevoisLib: Sending cmd " + cmdToSend.get());
				serialPort.writeString(cmdToSend.getAndSet(""));
				cmdPending.set(true);
				cmdResp.setLength(0);
				cmdReady.set(false);
			}
			
			if (serialPort.getBytesReceived() > 0) {
				String[] incomingLines = serialPort.readString().split("\r\n");
				//System.out.println("Lines rcv'd: " + incomingLines.length);
				for (String incoming : incomingLines) {
					//System.out.println("\tINCOMING: " + incoming);
					if (cmdPending.get()) {
						//System.out.println("\t\tAppending to cmdResp");
						cmdResp.append(incoming).append("\n");
						if (incoming.startsWith("OK") || incoming.startsWith("ERR")) {
							//System.out.println("\t\tClearing cmdPending");
							cmdPending.set(false);
						} 
					} else {
						//System.out.println("\t\tchecking for {}");
						if (incoming.startsWith("{") && incoming.contains("}")) {
							//System.out.println("\t\tcalling parseTargetInfo()");
							parseTargetInfo(incoming);
						} else {
							System.out.println("jevoisLib: USB rcv'd " + incoming);
						}
					}
				}
			}
			
			try { Thread.sleep(5); } catch (Exception e) {}
		}
		
		System.out.println("jevoisLib: Jevois Listener Thread exiting!");

	}
}