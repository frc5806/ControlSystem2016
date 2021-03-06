package org.usfirst.frc.team5806.robot;

import org.usfirst.frc.team5806.robot.TargetTracker.ParticleReport;

import edu.wpi.first.wpilibj.ADXRS450_Gyro;
import edu.wpi.first.wpilibj.CameraServer;
import edu.wpi.first.wpilibj.Compressor;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.Encoder;

import edu.wpi.first.wpilibj.IterativeRobot;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.SPI;
import edu.wpi.first.wpilibj.Talon;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.Victor;
import edu.wpi.first.wpilibj.interfaces.Accelerometer;
import edu.wpi.first.wpilibj.livewindow.LiveWindow;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.vision.USBCamera;

public class Robot extends IterativeRobot {
	private static double rampCoefficient = 0.07;
	private static final String CAMERA_NAME = "cam0";
	private static final double[] SHOOTING_RANGE_FEET = {3.75, 4.25};
	private static final double[] GOAL_CENTERED_COORDS = {500, 500};
	private static final double GOAL_CENTERED_ERROR = 30;

	private static final int AUTONOMOUS_GOAL_NUMBER = 1;
		
	private static double limitedJoyL, limitedJoyR; 

	Sonar[] sonars;
	
	Joystick joystick;
	RobotDrive drive;
	
	TargetTracker tracker;
	
	Compressor compressor;
	Roller roller;
	Arm arm;
	Stick stick;
	
	ButtonHandler buttonHandler;

	/*public boolean inShootingRange() {
		//assuming the robot is facing 90 deg toward wall
		double feetFromWall = sonars[0].getFeet();
		double[][] centerGuesses = finder.getGoalCenters();
		double minDist = Double.MAX_VALUE;
		for (int i = 0; i < centerGuesses.length; i++) {
			double[] coords = centerGuesses[i];
			double dist = Math.sqrt(Math.pow(GOAL_CENTERED_COORDS[0] - coords[0], 2) + 
							Math.pow(GOAL_CENTERED_COORDS[1] - coords[1], 2));
			if (dist < minDist) {
				minDist = dist;
			}
		}
		return feetFromWall >= SHOOTING_RANGE_FEET[0]
			&& feetFromWall <= SHOOTING_RANGE_FEET[1]
			&& minDist <= GOAL_CENTERED_ERROR;
	}*/
	
	public void robotInit() {
		sonars = new Sonar[] { new Sonar(3), new Sonar(0) };
		
		joystick = new Joystick(0);
		
		compressor = new Compressor();
		compressor.start();

		arm = new Arm(new DoubleSolenoid(6, 7), new DoubleSolenoid(0, 1));
		roller = new Roller(new Victor(2), new MagnetSensor(4));
		roller.enable();
		
		buttonHandler = new ButtonHandler(joystick);
		
		drive = new RobotDrive(
				new DriveTrain(new Victor(1), new Encoder(2, 3, true), 0, true), 
				new DriveTrain(new Victor(0), new Encoder(0, 1, true), 0, false), 
				sonars[0],
				sonars[1]);

		tracker = new TargetTracker();
		
		stick = new Stick(3);
	}
	
	public void autonomousInit() {
		try{
			arm.lower();
			Timer.delay(0.5);
			
			double driveSpeed = -0.8;
			double creepSpeed = -0.3;
			double turnSpeed = 0.6;
			
			double delay = 0.2;
			
			drive.moveDistance(220, -driveSpeed);
			
			/*Timer.delay(delay);
			drive.turn(90, -turnSpeed);
			Timer.delay(delay);
			drive.moveDistance(120, driveSpeed);
			Timer.delay(delay);
			drive.turn(90, turnSpeed);
			Timer.delay(delay);
			drive.moveDistance(90, driveSpeed);*/
			
			// Vision processing angle calibration
			/*int iterations = 0;
			double[] targetCenter = new double[]{150, 155};
			ParticleReport goalContour;
			do {
				goalContour = tracker.retrieveBestTarget();
				if(goalContour.centerX - targetCenter[0] > 0) drive.turn(-5, turnSpeed);
				else drive.turn(5, turnSpeed);
				
			} while(iterations++ < 20 && Math.abs(goalContour.centerX - targetCenter[0]) > GOAL_CENTERED_ERROR);*/
			
			/*while((sonars[0].getMM() + sonars[1].getMM()) / 2.0 > 350) {
				drive.moveDistance(10, creepSpeed);
			}
			
			arm.raise();
			Timer.delay(2);
			roller.forward();
			Timer.delay(Roller.TIME_TO_FULL_SPEED_MILLIS / 1000.0);
			arm.push();
			Timer.delay(2);*/
		} catch(Exception e) {
			drive.setSpeed(0);
			arm.lower();
		}
	}
	
	public void teleopInit() {
		limitedJoyL = 0.1;
		limitedJoyR = 0.1;
		
		Thread t = new Thread(new CameraShower(tracker));
		t.start();
	}

	public void teleopPeriodic() {
		// Listen to controls
		
		boolean gamepad = false;
		
		if (gamepad) {
			if(joystick.getRawAxis(2) > 0.7) {
				roller.forward();
			}
			if(buttonHandler.readButton('X')) {
				roller.reverse();
			}
			if(buttonHandler.readButton('Y')) {
				roller.stop();
			}
			
			if(joystick.getRawAxis(3) > 0.7) {
				arm.push();
			} else {
				arm.retract();
			}

			if(buttonHandler.readButton('A')){
				arm.raise();
			}
			
			if(buttonHandler.readButton('B')) {
				arm.lower();
			}
			
			if(buttonHandler.isDown('L')) {
				SmartDashboard.putString("Stick state", "lower");
				stick.lower();
			} else if (buttonHandler.isDown('R')) {
				stick.lift();
				SmartDashboard.putString("Stick state", "lift");
			} else {
				stick.stay();
				SmartDashboard.putString("Stick state", "stay");
			}
			
			// Using exponential moving averages for joystick limiting
			double desiredL = joystick.getRawAxis(1);
			double desiredR = joystick.getRawAxis(5);
			double errorL = desiredL - limitedJoyL;
			double errorR = desiredR - limitedJoyR;
			limitedJoyL += errorL * rampCoefficient;
			limitedJoyR += errorR * rampCoefficient;
			
			if(Math.abs(desiredL) > 0.25) drive.leftDrive.motorController.set(-desiredL);
			else drive.leftDrive.motorController.set(0);
			if(Math.abs(desiredR) > 0.25) drive.rightDrive.motorController.set(desiredR);
			else drive.rightDrive.motorController.set(0);
		} else {
			double turn = 0;
			double forwards = 0;
			
			if (Math.abs(joystick.getRawAxis(2)) > 0.1) {
				turn = joystick.getRawAxis(2);
			} if (Math.abs(joystick.getRawAxis(1)) > 0.1) {
				forwards = joystick.getRawAxis(1);
			}
						
			if(buttonHandler.readButton('X')) {
				roller.reverse();
			}
			if(buttonHandler.readButton('Y')) {
				roller.stop();
			}
			
			if(joystick.getRawAxis(3) > 0.7) {
				arm.lower();
			} else {
				arm.raise();
			}

			if(buttonHandler.readButton('A')){
				arm.push();
				new java.util.Timer().schedule(
					new java.util.TimerTask() {
						@Override
						public void run() {
							arm.retract();
						}
					}, 500
				);
			} 
			
			if(buttonHandler.readButton('B')) {
				roller.forward();
			}
			
			if(buttonHandler.isDown('L')) {
				SmartDashboard.putString("Stick state", "lower");
				stick.lower();
			} else if (buttonHandler.isDown('R')) {
				stick.lift();
				SmartDashboard.putString("Stick state", "lift");
			} else {
				stick.stay();
				SmartDashboard.putString("Stick state", "stay");
			}

			double leftValue = forwards - turn;
			double rightValue = forwards + turn;
			
			drive.leftDrive.motorController.set(-leftValue);
			drive.rightDrive.motorController.set(rightValue);
		}
		
		
		//drive.leftDrive.getPIDController().setPID(1, 0, 0);

		// Update dashboardasdf
		SmartDashboard.putNumber("Left Sonar", sonars[0].getMM());
		SmartDashboard.putNumber("Right Sonar", sonars[1].getMM());
		SmartDashboard.putNumber("Left Encoder", drive.leftDrive.encoder.get());
		SmartDashboard.putNumber("Right Encoder", drive.rightDrive.encoder.get());
		SmartDashboard.putNumber("Left Feedback", drive.leftDrive.feedback);
		SmartDashboard.putNumber("Right Feedback", drive.rightDrive.feedback);
		SmartDashboard.putNumber("Left Target Encoder", drive.leftDrive.targetEncoderSpeed);
		SmartDashboard.putNumber("Right Target Encoder", drive.rightDrive.targetEncoderSpeed);
		SmartDashboard.putNumber("Left Current Encoder", drive.leftDrive.encoderSpeed);
		SmartDashboard.putNumber("Right Current Encoder", drive.rightDrive.encoderSpeed);
		SmartDashboard.putNumber("Left Current", drive.leftDrive.motorController.get());
		SmartDashboard.putNumber("Right Current", drive.rightDrive.motorController.get());
		//SmartDashboard.putNumber("Left Joystick", desiredL);
		//SmartDashboard.putNumber("Right Joystick", desiredR);
		
		//tracker.showImage();
	}

	public void disableInit() {
		compressor.stop();
	}
}
