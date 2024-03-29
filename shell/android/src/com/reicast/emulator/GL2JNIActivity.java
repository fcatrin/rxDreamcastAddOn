package com.reicast.emulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.reicast.emulator.config.Config;
import com.reicast.emulator.emu.GL2JNIView;
import com.reicast.emulator.emu.JNIdc;
import com.reicast.emulator.emu.OnScreenMenu;
import com.reicast.emulator.emu.OnScreenMenu.FpsPopup;
import com.reicast.emulator.emu.OnScreenMenu.MainPopup;
import com.reicast.emulator.emu.OnScreenMenu.VmuPopup;
import com.reicast.emulator.periph.Gamepad;
import com.reicast.emulator.periph.MOGAInput;
import com.reicast.emulator.periph.SipEmulator;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.PopupWindow;
import retrobox.utils.GamepadInfoDialog;
import retrobox.utils.ListOption;
import retrobox.utils.RetroBoxDialog;
import retrobox.utils.RetroBoxUtils;
import retrobox.vinput.AnalogGamepad;
import retrobox.vinput.AnalogGamepad.Axis;
import retrobox.vinput.AnalogGamepadListener;
import retrobox.vinput.GamepadDevice;
import retrobox.vinput.GamepadMapping.Analog;
import retrobox.vinput.Mapper;
import retrobox.vinput.Mapper.ShortCut;
import retrobox.vinput.VirtualEvent.MouseButton;
import retrobox.vinput.VirtualEventDispatcher;
import retrox.reicast.emulator.R;
import tv.ouya.console.api.OuyaController;
import xtvapps.core.AndroidCoreUtils;
import xtvapps.core.AndroidFonts;
import xtvapps.core.Callback;
import xtvapps.core.SimpleCallback;
import xtvapps.core.content.KeyValue;

@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
public class GL2JNIActivity extends Activity {
	public GL2JNIView mView;
	OnScreenMenu menu;
	public MainPopup popUp;
	VmuPopup vmuPop;
	FpsPopup fpsPop;
	MOGAInput moga = new MOGAInput();
	private SharedPreferences prefs;
	
	private Config config;
	private Gamepad pad = new Gamepad();

	public static byte[] syms;
	
	private boolean isRetroX = false;
	private VirtualInputDispatcher vinputDispatcher;
	private AnalogGamepad analogGamepad;
	private Mapper mapper;
	
	@Override
	protected void onCreate(Bundle icicle) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (prefs.getInt(Config.pref_rendertype, 2) == 2) {
			getWindow().setFlags(
					WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
					WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
		}
		
		isRetroX = getIntent().getBooleanExtra("retrox", false);
		
		config = new Config(GL2JNIActivity.this);
		config.getConfigurationPrefs();
		menu = new OnScreenMenu(GL2JNIActivity.this, prefs);

		pad.isXperiaPlay = pad.IsXperiaPlay();
		pad.isOuyaOrTV = pad.IsOuyaOrTV(GL2JNIActivity.this);
//		pad.isNvidiaShield = pad.IsNvidiaShield();

		/*
		 * try { //int rID =
		 * getResources().getIdentifier("fortyonepost.com.lfas:raw/syms.map",
		 * null, null); //get the file as a stream InputStream is =
		 * getResources().openRawResource(R.raw.syms);
		 * 
		 * syms = new byte[(int) is.available()]; is.read(syms); is.close(); }
		 * catch (IOException e) { e.getMessage(); e.printStackTrace(); }
		 */
		

		String fileName = null;

		// Call parent onCreate()
		super.onCreate(icicle);
		OuyaController.init(this);

		// Populate device descriptor-to-player-map from preferences
		pad.deviceDescriptor_PlayerNum.put(
				prefs.getString(Gamepad.pref_player1, null), 0);
		pad.deviceDescriptor_PlayerNum.put(
				prefs.getString(Gamepad.pref_player2, null), 1);
		pad.deviceDescriptor_PlayerNum.put(
				prefs.getString(Gamepad.pref_player3, null), 2);
		pad.deviceDescriptor_PlayerNum.put(
				prefs.getString(Gamepad.pref_player4, null), 3);
		pad.deviceDescriptor_PlayerNum.remove(null);

		moga.onCreate(this, pad);
		moga.mListener.setPlayerNum(1);

		boolean controllerTwoConnected = false;
		boolean controllerThreeConnected = false;
		boolean controllerFourConnected = false;
		
		if (isRetroX) {
			controllerTwoConnected   = Mapper.hasGamepads();
			/*
			controllerTwoConnected   = Mapper.hasGamepad(1);
			controllerThreeConnected = Mapper.hasGamepad(2);
			controllerFourConnected  = Mapper.hasGamepad(3);
			*/
		} else {

			for (HashMap.Entry<String, Integer> e : pad.deviceDescriptor_PlayerNum
					.entrySet()) {
				String descriptor = e.getKey();
				Integer playerNum = e.getValue();
	
				switch (playerNum) {
				case 1:
					if (descriptor != null)
						controllerTwoConnected = true;
					break;
				case 2:
					if (descriptor != null)
						controllerThreeConnected = true;
					break;
				case 3:
					if (descriptor != null)
						controllerFourConnected = true;
					break;
				}
			}
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {

			JNIdc.initControllers(new boolean[] { controllerTwoConnected,
					controllerThreeConnected, controllerFourConnected });
			int joys[] = InputDevice.getDeviceIds();
			for (int joy: joys) {
				String descriptor = null;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
					descriptor = InputDevice.getDevice(joy).getDescriptor();
				} else {
					descriptor = InputDevice.getDevice(joy).getName();
				}
				Log.d("reicast", "InputDevice ID: " + joy);
				Log.d("reicast",
						"InputDevice Name: "
								+ InputDevice.getDevice(joy).getName());
				Log.d("reicast", "InputDevice Descriptor: " + descriptor);
				pad.deviceId_deviceDescriptor.put(joy, descriptor);
			}

			for (int joy :joys) {
				Integer playerNum = pad.deviceDescriptor_PlayerNum
						.get(pad.deviceId_deviceDescriptor.get(joy));

				if (playerNum != null) {
					String id = pad.portId[playerNum];
					pad.custom[playerNum] = prefs.getBoolean(Gamepad.pref_js_modified + id, false);
					pad.compat[playerNum] = prefs.getBoolean(Gamepad.pref_js_compat + id, false);
					pad.joystick[playerNum] = prefs.getBoolean(Gamepad.pref_js_merged + id, false);
					if (InputDevice.getDevice(joy).getName()
							.contains(Gamepad.controllers_gamekey)) {
						if (pad.custom[playerNum]) {
							pad.setCustomMapping(id, playerNum, prefs);
						} else {
							pad.map[playerNum] = pad.getConsoleController();
						}
					} else if (!pad.compat[playerNum]) {
						if (pad.custom[playerNum]) {
							pad.setCustomMapping(id, playerNum, prefs);
						} else if (InputDevice.getDevice(joy).getName()
								.equals(Gamepad.controllers_sony)) {
							pad.map[playerNum] = pad.getConsoleController();
						} else if (InputDevice.getDevice(joy).getName()
								.equals(Gamepad.controllers_xbox)) {
							pad.map[playerNum] = pad.getConsoleController();
						} else if (InputDevice.getDevice(joy).getName()
								.contains(Gamepad.controllers_shield)) {
							pad.map[playerNum] = pad.getConsoleController();
						} else if (InputDevice.getDevice(joy).getName()
								.contains(Gamepad.controllers_play)) {
							pad.map[playerNum] = pad.getXPlayController();
						} else if (!pad.isActiveMoga[playerNum]) { // Ouya controller
							pad.map[playerNum] = pad.getOUYAController();
						}
					} else {
						pad.getCompatibilityMap(playerNum, id, prefs);
					}
					pad.initJoyStickLayout(playerNum);
				} else {
					pad.runCompatibilityMode(joy, prefs);
				}
			}
			if (joys.length == 0) {
				pad.fullCompatibilityMode(prefs);
			}
		} else {
			pad.fullCompatibilityMode(prefs);
		}

		
		if (isRetroX && !Config.fromretrox) { // only use default value if the user has never changed the value
			Config.widescreen = !getIntent().getBooleanExtra("keepAspect", true);
		}
		
		config.loadConfigurationPrefs();


		// When viewing a resource, pass its URI to the native code for opening
		if (getIntent().getAction().equals("com.reicast.EMULATOR"))
			fileName = Uri.decode(getIntent().getData().toString());

		// Create the actual GLES view
		mView = new GL2JNIView(GL2JNIActivity.this, config, fileName, false,
				prefs.getInt(Config.pref_renderdepth, 24), 0, false);
		
		boolean retroXShowFPS = false;
		if (isRetroX) {
			retroXShowFPS = getIntent().getBooleanExtra(Config.pref_showfps, false);
			
			setContentView(R.layout.game_view);
			ViewGroup containerView = (ViewGroup)findViewById(R.id.game_view);
			containerView.addView(mView, 0);
			
        	vinputDispatcher = new VirtualInputDispatcher();
        	
            mapper = new Mapper(getIntent(), vinputDispatcher);
            Mapper.initGestureDetector(this);
            
        	analogGamepad = new AnalogGamepad(0, 0, new AnalogGamepadListener() {
    			
    			@Override
    			public void onMouseMoveRelative(float mousex, float mousey) {}
    			
    			@Override
    			public void onMouseMove(int mousex, int mousey) {}
    			
    			@Override
    			public void onAxisChange(GamepadDevice gamepad, float axisx, float axisy, float hatx, float haty, float raxisx, float raxisy) {
					vinputDispatcher.sendAnalog(gamepad, Analog.LEFT, axisx, -axisy, hatx, haty);
					vinputDispatcher.sendAnalog(gamepad, Analog.RIGHT, raxisx, raxisy, 0, 0);
    			}

				@Override
				public void onDigitalX(GamepadDevice gamepad, Axis axis, boolean on) {}

				@Override
				public void onDigitalY(GamepadDevice gamepad, Axis axis, boolean on) {}
				
				@Override
				public void onTriggers(String deviceDescriptor, int deviceId, boolean left, boolean right) {}
				
				@Override
				public void onTriggersAnalog(GamepadDevice gamepad , int deviceId, float left, float right) {
					vinputDispatcher.sendTriggers(gamepad, deviceId, left, right); 
				}

    		});
		} else {
			setContentView(mView);
		}

		//setup mic
		boolean micPluggedIn = prefs.getBoolean(Config.pref_mic, false);
		if(micPluggedIn){
			SipEmulator sip = new SipEmulator();
			sip.startRecording();
			JNIdc.setupMic(sip);
		}
		
		popUp = menu.new MainPopup(this);
		vmuPop = menu.new VmuPopup(this);
		if(prefs.getBoolean(Config.pref_vmu, false)){
			//kind of a hack - if the user last had the vmu on screen
			//inverse it and then "toggle"
			prefs.edit().putBoolean(Config.pref_vmu, false).commit();
			//can only display a popup after onCreate
			mView.post(new Runnable() {
				public void run() {
					toggleVmu();
				}
			});
		}
		JNIdc.setupVmu(menu.getVmu());
		if (prefs.getBoolean(Config.pref_showfps, false) || retroXShowFPS) {
			fpsPop = menu.new FpsPopup(this);
			mView.setFpsDisplay(fpsPop);
			mView.post(new Runnable() {
				public void run() {
					displayFPS();
				}
			});
		}
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (isRetroX) {
	    	if (RetroBoxDialog.isDialogVisible(this)) {
	    		return super.onGenericMotionEvent(event);
	    	}
	    	
			if (analogGamepad != null && analogGamepad.onGenericMotionEvent(event)) return true;
			return super.onGenericMotionEvent(event);
			
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {

			Integer playerNum = Arrays.asList(pad.name).indexOf(event.getDeviceId());
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && playerNum == -1) {
				playerNum = pad.deviceDescriptor_PlayerNum
					.get(pad.deviceId_deviceDescriptor.get(event.getDeviceId()));
			} else {
				playerNum = -1;
			}

			if (playerNum == null || playerNum == -1)
				return false;

			if (!pad.compat[playerNum]) {

				// Joystick
				if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {

					// do other things with joystick
					float LS_X = event.getAxisValue(OuyaController.AXIS_LS_X);
					float LS_Y = event.getAxisValue(OuyaController.AXIS_LS_Y);
					float RS_X = event.getAxisValue(OuyaController.AXIS_RS_X);
					float RS_Y = event.getAxisValue(OuyaController.AXIS_RS_Y);
					float L2 = event.getAxisValue(OuyaController.AXIS_L2);
					float R2 = event.getAxisValue(OuyaController.AXIS_R2);

					if (!pad.joystick[playerNum]) {
						pad.previousLS_X[playerNum] = pad.globalLS_X[playerNum];
						pad.previousLS_Y[playerNum] = pad.globalLS_Y[playerNum];
						pad.globalLS_X[playerNum] = LS_X;
						pad.globalLS_Y[playerNum] = LS_Y;
					}

					GL2JNIView.jx[playerNum] = (int) (LS_X * 126);
					GL2JNIView.jy[playerNum] = (int) (LS_Y * 126);
					
					GL2JNIView.lt[playerNum] = (int) (L2 * 255);
					GL2JNIView.rt[playerNum] = (int) (R2 * 255);

					if (prefs.getBoolean(Gamepad.pref_js_rbuttons + pad.portId[playerNum], true)) {
						if (RS_Y > 0.25) {
							handle_key(playerNum, pad.map[playerNum][0]/* A */, true);
							pad.wasKeyStick[playerNum] = true;
						} else if (RS_Y < 0.25) {
							handle_key(playerNum, pad.map[playerNum][1]/* B */, true);
							pad.wasKeyStick[playerNum] = true;
						} else if (pad.wasKeyStick[playerNum]){
							handle_key(playerNum, pad.map[playerNum][0], false);
							handle_key(playerNum, pad.map[playerNum][1], false);
							pad.wasKeyStick[playerNum] = false;
						}
					} else {
						if (RS_Y > 0.25) {
							GL2JNIView.rt[playerNum] = (int) (RS_Y * 255);
							GL2JNIView.lt[playerNum] = (int) (L2 * 255);
						} else if (RS_Y < 0.25) {
							GL2JNIView.rt[playerNum] = (int) (R2 * 255);
							GL2JNIView.lt[playerNum] = (int) (-(RS_Y) * 255);
						}
					}
				}

			}
			mView.pushInput();
			if (!pad.joystick[playerNum] && (pad.globalLS_X[playerNum] == pad.previousLS_X[playerNum] && pad.globalLS_Y[playerNum] == pad.previousLS_Y[playerNum])
					|| (pad.previousLS_X[playerNum] == 0.0f && pad.previousLS_Y[playerNum] == 0.0f))
				// Only handle Left Stick on an Xbox 360 controller if there was
				// some actual motion on the stick,
				// so otherwise the event can be handled as a DPAD event
				return false;
			else
				return true;

		} else {
			return false;
		}
	}
	
	public boolean motionEventHandler(Integer playerNum, com.bda.controller.MotionEvent event) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {

			if (playerNum == null || playerNum == -1)
				return false;

			if (!pad.compat[playerNum]) {

				// do other things with joystick
				float LS_X = event.getAxisValue(OuyaController.AXIS_LS_X);
				float LS_Y = event.getAxisValue(OuyaController.AXIS_LS_Y);
				float RS_X = event.getAxisValue(OuyaController.AXIS_RS_X);
				float RS_Y = event.getAxisValue(OuyaController.AXIS_RS_Y);
				float L2 = event.getAxisValue(OuyaController.AXIS_L2);
				float R2 = event.getAxisValue(OuyaController.AXIS_R2);

				if (!pad.joystick[playerNum]) {
					pad.previousLS_X[playerNum] = pad.globalLS_X[playerNum];
					pad.previousLS_Y[playerNum] = pad.globalLS_Y[playerNum];
					pad.globalLS_X[playerNum] = LS_X;
					pad.globalLS_Y[playerNum] = LS_Y;
				}

				GL2JNIView.jx[playerNum] = (int) (LS_X * 126);
				GL2JNIView.jy[playerNum] = (int) (LS_Y * 126);

				GL2JNIView.lt[playerNum] = (int) (L2 * 255);
				GL2JNIView.rt[playerNum] = (int) (R2 * 255);

				if (prefs.getBoolean(Gamepad.pref_js_rbuttons + pad.portId[playerNum], true)) {
					if (RS_Y > 0.25) {
						handle_key(playerNum, pad.map[playerNum][0]/* A */, true);
						pad.wasKeyStick[playerNum] = true;
					} else if (RS_Y < 0.25) {
						handle_key(playerNum, pad.map[playerNum][1]/* B */, true);
						pad.wasKeyStick[playerNum] = true;
					} else if (pad.wasKeyStick[playerNum]){
						handle_key(playerNum, pad.map[playerNum][0], false);
						handle_key(playerNum, pad.map[playerNum][1], false);
						pad.wasKeyStick[playerNum] = false;
					}
				} else {
					if (RS_Y > 0.25) {
						GL2JNIView.rt[playerNum] = (int) (RS_Y * 255);
						GL2JNIView.lt[playerNum] = (int) (L2 * 255);
					} else if (RS_Y < 0.25) {
						GL2JNIView.rt[playerNum] = (int) (R2 * 255);
						GL2JNIView.lt[playerNum] = (int) (-(RS_Y) * 255);
					}
				}

			}
			mView.pushInput();
			if (!pad.joystick[playerNum] && (pad.globalLS_X[playerNum] == pad.previousLS_X[playerNum] && pad.globalLS_Y[playerNum] == pad.previousLS_Y[playerNum])
					|| (pad.previousLS_X[playerNum] == 0.0f && pad.previousLS_Y[playerNum] == 0.0f))
				// Only handle Left Stick on an Xbox 360 controller if there was
				// some actual motion on the stick,
				// so otherwise the event can be handled as a DPAD event
				return false;
			else
				return true;

		} else {
			return false;
		}
	}
	
	public boolean simulatedTouchEvent(int playerNum, float L2, float R2) {
		GL2JNIView.lt[playerNum] = (int) (L2 * 255);
		GL2JNIView.rt[playerNum] = (int) (R2 * 255);
		mView.pushInput();
		return true;
	}

	public boolean handle_key(Integer playerNum, int kc, boolean down) {
		if (playerNum == null || playerNum == -1)
			return false;
		if (kc == pad.getSelectButtonCode()) {
			return false;
		}

		boolean rav = false;
		for (int i = 0; i < pad.map[playerNum].length; i += 2) {
			if (pad.map[playerNum][i + 0] == kc) {
				if (down)
					GL2JNIView.kcode_raw[playerNum] &= ~pad.map[playerNum][i + 1];
				else
					GL2JNIView.kcode_raw[playerNum] |= pad.map[playerNum][i + 1];
				rav = true;
				break;
			}
		}
		mView.pushInput();
		return rav;

	}
	
	public void displayPopUp(PopupWindow popUp) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			popUp.showAtLocation(mView, Gravity.BOTTOM, 0, 60);
		} else {
			popUp.showAtLocation(mView, Gravity.BOTTOM, 0, 0);
		}
		popUp.update(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT);
	}
	
	public void displayDebug(PopupWindow popUpDebug) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			popUpDebug.showAtLocation(mView, Gravity.BOTTOM, 0, 60);
		} else {
			popUpDebug.showAtLocation(mView, Gravity.BOTTOM, 0, 0);
		}
		popUpDebug.update(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT);
	}

	public void displayFPS() {
		fpsPop.showAtLocation(mView, Gravity.TOP | Gravity.LEFT, 20, 20);
		fpsPop.update(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
	}

	public void toggleVmu() {
		boolean showFloating = !prefs.getBoolean(Config.pref_vmu, false);
		if (showFloating) {
			if (popUp.isShowing()) {
				popUp.dismiss();
			}
			//remove from popup menu
			popUp.hideVmu();
			//add to floating window
			vmuPop.showVmu();
			vmuPop.showAtLocation(mView, Gravity.TOP | Gravity.RIGHT, 4, 4);
			vmuPop.update(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		} else {
			vmuPop.dismiss();
			//remove from floating window
			vmuPop.hideVmu();
			//add back to popup menu
			popUp.showVmu();
		}
		prefs.edit().putBoolean(Config.pref_vmu, showFloating).commit();
	}
	
	public void displayConfig(PopupWindow popUpConfig) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			popUpConfig.showAtLocation(mView, Gravity.BOTTOM, 0, 60);
		} else {
			popUpConfig.showAtLocation(mView, Gravity.BOTTOM, 0, 0);
		}
		popUpConfig.update(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT);
	}

	private boolean handleRetroXKey(int keyCode, KeyEvent event) {
        boolean keyDown = event.getAction() == KeyEvent.ACTION_DOWN;
        
    	if (RetroBoxDialog.isDialogVisible(this)) {
    		if (keyDown) {
    			return RetroBoxDialog.onKeyDown(this, keyCode, event);
    		} else {
    			return RetroBoxDialog.onKeyUp(this, keyCode, event);
    		}
    	}
    	return mapper.handleKeyEvent(this, event, keyCode, keyDown);
	}
	
	public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isRetroX) {
        	return handleRetroXKey(keyCode, event) || super.onKeyUp(keyCode, event);
        } else {
			Integer playerNum = Arrays.asList(pad.name).indexOf(event.getDeviceId());
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && playerNum == -1) {
				playerNum = pad.deviceDescriptor_PlayerNum
					.get(pad.deviceId_deviceDescriptor.get(event.getDeviceId()));
			} else {
				playerNum = -1;
			}
	
			if (playerNum != null && playerNum != -1) {
				if (pad.compat[playerNum] || pad.custom[playerNum]) {
					String id = pad.portId[playerNum];
					if (keyCode == prefs.getInt(Gamepad.pref_button_l + id,
							KeyEvent.KEYCODE_BUTTON_L1)
							|| keyCode == prefs.getInt(Gamepad.pref_button_r + id,
									KeyEvent.KEYCODE_BUTTON_R1)) {
						return simulatedTouchEvent(playerNum, 0.0f, 0.0f);
					}
				}
			}
	
			return handle_key(playerNum, keyCode, false)
					|| super.onKeyUp(keyCode, event);
        }
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isRetroX) {
			if (keyCode == KeyEvent.KEYCODE_BACK) {
				if (!RetroBoxDialog.cancelDialog(this)) {
					openRetroBoxMenu(true);
				}
		    	return true;
			}
        	return handleRetroXKey(keyCode, event) || super.onKeyDown(keyCode, event);
        } else {

			Integer playerNum = Arrays.asList(pad.name).indexOf(event.getDeviceId());
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && playerNum == -1) {
				playerNum = pad.deviceDescriptor_PlayerNum
					.get(pad.deviceId_deviceDescriptor.get(event.getDeviceId()));
			} else {
				playerNum = -1;
			}
	
			if (playerNum != null && playerNum != -1) {
				if (pad.compat[playerNum] || pad.custom[playerNum]) {
					String id = pad.portId[playerNum];
					if (keyCode == prefs.getInt(Gamepad.pref_button_l + id, KeyEvent.KEYCODE_BUTTON_L1)) {
						return simulatedTouchEvent(playerNum, 1.0f, 0.0f);
					}
					if (keyCode == prefs.getInt(Gamepad.pref_button_r + id, KeyEvent.KEYCODE_BUTTON_R1)) {
						return simulatedTouchEvent(playerNum, 0.0f, 1.0f);
					}
				}
			}
	
			if (handle_key(playerNum, keyCode, true)) {
				if (playerNum == 0)
					JNIdc.hide_osd();
				return true;
			}
	
			if (keyCode == pad.getSelectButtonCode()) {
				return showMenu();
			} 
			if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1
					|| (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH 
					&& ViewConfiguration.get(this).hasPermanentMenuKey())) {
				if (keyCode == KeyEvent.KEYCODE_MENU) {
					return showMenu();
				}
			}
			if (keyCode == KeyEvent.KEYCODE_BACK) {
				if (pad.isXperiaPlay) {
					return true;
				} else {
					return showMenu();
				}
			}
			return super.onKeyDown(keyCode, event);
        }
	}

	public GL2JNIView getGameView() {
		return mView;
	}

	public void screenGrab() {
		mView.screenGrab();
	}
	
	private boolean showMenu() {
		if (popUp != null) {
			if (!menu.dismissPopUps()) {
				if (!popUp.isShowing()) {
					displayPopUp(popUp);
				} else {
					popUp.dismiss();
				}
			} else {
				popUp.dismiss();
			}
		}
		return true;
	}

	@Override
	protected void onPause() {
		super.onPause();
		mView.onPause();
		moga.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		moga.onDestroy();
	}

	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		JNIdc.stop();
		mView.onStop();
		super.onStop();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mView.onResume();
		moga.onResume();
	}
	
    private void openRetroBoxMenu(final boolean pause) {
    	new Handler().postDelayed(new Runnable(){

			@Override
			public void run() {
				openRetroBoxMenuPost(pause);
			}
		}, 100);
    }
    
    
	private void uiQuit() {
		finish();
	}
	
	protected void pushRetroXView(int resourceId) {
    	// must be done this way, if not, there is a black hole in the GL surface after closing the popup
    	// only adding and removing the view makes the GL surface resets its state

		final ViewGroup containerView = (ViewGroup)findViewById(R.id.game_view);
    	int childIndex = 0;
    	while (childIndex < containerView.getChildCount()) {
    		View view = containerView.getChildAt(childIndex);
    		Object tag = containerView.getTag();
    		if (tag instanceof String && "rxtag".equals(tag)) {
    			containerView.removeView(view);
    			continue;
    		}
    		childIndex++;
    	}
    	
    	// add new view if there is any
    	if (resourceId > 0) {
    		View view = getLayoutInflater().inflate(resourceId, null);
    		view.setTag("rxtag");
    		containerView.addView(view, 1);

    		if (resourceId == R.layout.modal_dialog_list) {
    			AndroidFonts.setViewFont(findViewById(R.id.txtDialogListTitle), RetroBoxUtils.FONT_DEFAULT_M);
    		}
    	}
	}
	
    protected void uiHelp() {
    	pushRetroXView(R.layout.modal_dialog_gamepad);
    	
    	GamepadInfoDialog gamepadInfoDialog = new GamepadInfoDialog(this);
        gamepadInfoDialog.loadFromIntent(getIntent());

		RetroBoxDialog.showGamepadDialogIngame(this, gamepadInfoDialog, Mapper.hasGamepads(), new SimpleCallback() {
			
			@Override
			public void onResult() {}

			@Override
			public void onFinally() {
				closeRetroXMenu();
			}
			
		});
    }

    protected void closeRetroXMenu() {
    	ViewGroup containerView = (ViewGroup)findViewById(R.id.game_view);
    	containerView.removeViewAt(1);
    	onResume();
    }
    
    private void openRetroBoxMenuPost(boolean pause) {
    	if (pause) onPause();

    	pushRetroXView(R.layout.modal_dialog_list);
    	
    	List<ListOption> options = new ArrayList<ListOption>();
    	options.add(new ListOption("", getString(R.string.emu_opt_cancel)));
    	options.add(new ListOption("wide", "Screen Size", !Config.widescreen ? "Set Wide Screen 16:9":"Set Original 4:3")); // TODO Translate
    	options.add(new ListOption("help", getString(R.string.emu_opt_help)));
    	options.add(new ListOption("quit", getString(R.string.emu_opt_quit)));
    	
    	RetroBoxDialog.showListDialog(this, getString(R.string.emu_opt_title), options, new Callback<KeyValue>() {
			@Override
			public void onResult(KeyValue result) {
				String key = result.getKey();
				if (key.equals("quit")) {
					uiQuit();
					return;
				} else if (key.equals("help")) {
					uiHelp();
					return;
				} else if (key.equals("wide")) {
					Config.widescreen = !Config.widescreen;
					config.saveRetroXSettings();

					JNIdc.widescreen(Config.widescreen ? 1 : 0);
					if (Config.widescreen) {
						AndroidCoreUtils.toast(GL2JNIActivity.this, "Notice: Not all games are compatible with wide screen mode"); // TODO Translate
					}
				}
				closeRetroXMenu();
			}

			@Override
			public void onError() {
				closeRetroXMenu();
			}
		});
    }
    
	class VirtualInputDispatcher implements VirtualEventDispatcher {
    	int maskMap[] = {
    			Gamepad.key_CONT_DPAD_UP, Gamepad.key_CONT_DPAD_DOWN, Gamepad.key_CONT_DPAD_LEFT, Gamepad.key_CONT_DPAD_RIGHT,
    			Gamepad.key_CONT_A, Gamepad.key_CONT_B, Gamepad.key_CONT_X, Gamepad.key_CONT_Y,
    			0, 0, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
    			0, 0, 0, Gamepad.key_CONT_START
    	};
    	
    	private int DPAD_UP    = 0;
    	private int DPAD_DOWN  = 1;
    	private int DPAD_LEFT  = 2;
    	private int DPAD_RIGHT = 3;
    	
    	
    	public boolean[][] buttons = new boolean[4][maskMap.length];
    	
    	private static final int ANALOG_MAX_X = 126;
    	private static final int ANALOG_MAX_Y = 126;
    	int analogX[] = new int[4];
    	int analogY[] = new int[4];
    	int tl[] = new int[4];
    	int tr[] = new int[4];
    	int analogTL[] = new int[4];
    	int analogTR[] = new int[4];

    	@Override
    	public void sendAnalog(GamepadDevice gamepad, Analog index, double x, double y, double hatx, double haty) {
    		int player = gamepad.player;
    		
    		if (index == Analog.LEFT) {
    		
	    		int newX = (int)(ANALOG_MAX_X * x);
	    		int newY = (int)(ANALOG_MAX_Y * y);

	    		analogX[player] = newX;
	    		analogY[player] = -newY;
	    		
	    		buttons[player][DPAD_UP]    = haty < 0;
	    		buttons[player][DPAD_DOWN]  = haty > 0;
	    		
	    		buttons[player][DPAD_LEFT]  = hatx < 0;
	    		buttons[player][DPAD_RIGHT] = hatx > 0;
	    		
    		}
    		notifyChange(player);
    	};
    	
    	public void sendTriggers(GamepadDevice gamepad, int deviceId, float left, float right) {
    		int player = gamepad.player;
			analogTL[player] = (int)(left * 255);
			analogTR[player] = (int)(right * 255);
			notifyChange(player);
		}

		private void notifyChange(int player) {
    		GL2JNIView.jx[player] = analogX[player];
    		GL2JNIView.jy[player] = analogY[player];
    		
    		int state = 0xFFFF;
    		for(int i=0; i<buttons[player].length; i++) {
    			boolean isPressed = buttons[player][i];
    			
    			if (maskMap[i] == MotionEvent.AXIS_LTRIGGER) {
    				tl[player] = isPressed ? 255 : 0;
    			} else if (maskMap[i] == MotionEvent.AXIS_RTRIGGER) {
    				tr[player] = isPressed ? 255 : 0;
    			} else {
    				if (isPressed) state &= ~maskMap[i];
    			}
    		}

    		GL2JNIView.lt[player] = analogTL[player] > 0 ? analogTL[player] : tl[player];
    		GL2JNIView.rt[player] = analogTR[player] > 0 ? analogTR[player] : tr[player];
    		
    		GL2JNIView.kcode_raw[player] = state;
    		
    		/*
    		Log.d("INPUT", GL2JNIView.jx[player] + ", " + GL2JNIView.jy[player] + " l:" + GL2JNIView.lt[player] + " r:" + GL2JNIView.rt[player] +
    				" " + GL2JNIView.kcode_raw[player]);
    		*/
    		
    		mView.pushInput();
    	}
    	
		@Override
		public void sendKey(GamepadDevice gamepad, int keyCode, boolean down) {
			int index = gamepad.getGamepadMapping().getOriginIndex(keyCode);
			if (index>=0) {
				buttons[gamepad.player][index] = down;
				notifyChange(gamepad.player);
			}
		}

		@Override
		public void sendMouseButton(MouseButton button, boolean down) {}

		@Override
		public boolean handleShortcut(ShortCut shortcut, boolean down) {
			switch(shortcut) {
			case EXIT: if (!down) uiQuit(); return true;
			case MENU : if (!down) openRetroBoxMenu(true); return true;
			default:
				return false;
			}
		}
    }

    
}
