package org.usfirst.frc.team687.robot.subsystems;

import org.usfirst.frc.team687.util.Jevois;

import edu.wpi.cscore.VideoMode.PixelFormat;
import edu.wpi.first.wpilibj.SerialPort;
import edu.wpi.first.wpilibj.command.Subsystem;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
/**
 *
 */
public class SerialReader extends Subsystem {	
//	static final int BAUD_RATE = 115200;
//	 	
//	SerialPort visionPort;
//	//	int loopCount = 0;
//	 	
//	public void initDefaultCommand() {
//	// Set the default command for a subsystem here.
//	//setDefaultCommand(new MySpecialCommand());
//	     	
//		try {
//	 		System.out.print("Creating JeVois SerialPort...");
//	 		visionPort = new SerialPort(BAUD_RATE,SerialPort.Port.kUSB);
//	 		System.out.println("Connected...");
//	 	} catch (Exception e) {
//	 		System.out.println("FAILED!! No device detected...");
//	        e.printStackTrace();
//	    }
//	}
//	     
//	public void writeSerial(String input) {
//		if (visionPort == null) return;
//		System.out.println("pinging JeVois");
//	    String cmd = input;
//	    int bytes = visionPort.writeString(cmd);
//	    System.out.println("wrote " +  bytes + "/" + cmd.length() + " bytes, cmd: " + cmd);
//	}
//	    
//	public String readSerial() {
//		String output = visionPort.readString();
//	     return output;
//	}
//	     
//	public void displayOutputs() {
//	     SmartDashboard.putString("USB output", readSerial()); 
//	}
//	
//	Jevois jevois;
	Thread thread = null;
	PixelFormat pixelFormat = PixelFormat.kYUYV;
	int pixelLength = 320;
	int pixelWidth = 254;
	int frameRate = 60;
	
    public void initDefaultCommand() {
        // Set the default command for a subsystem here.
        //setDefaultCommand(new MySpecialCommand());
		try {
			thread = new Thread(new Jevois());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	thread.start();
    }
//    	jevois.setVideoMode(pixelFormat, pixelLength, pixelWidth, frameRate);
//        
//    }
//    
//    public void getTargetInfo() {
//    	jevois.streamTargetInfo(true);
//    }
//    
//    public void ping() {
//        jevois.ping();    	
//    }
}