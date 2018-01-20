package org.usfirst.frc.team687.robot.subsystems;

import org.usfirst.frc.team687.util.Jevois;

import edu.wpi.cscore.VideoMode.PixelFormat;
import edu.wpi.first.wpilibj.command.Subsystem;

/**
 *
 */
public class SerialReader extends Subsystem {
	
	Jevois jevois;
	PixelFormat pixelFormat = PixelFormat.kMJPEG;
	int pixelLength = 320;
	int pixelWidth = 254;
	int frameRate = 60;
	
    public void initDefaultCommand() {
        // Set the default command for a subsystem here.
        //setDefaultCommand(new MySpecialCommand());
    	
    	try {
			jevois = new Jevois();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        jevois.setVideoMode(pixelFormat, pixelLength, pixelWidth, frameRate);
        jevois.run();
    }
}