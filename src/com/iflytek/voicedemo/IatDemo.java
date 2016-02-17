package com.iflytek.voicedemo;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import org.json.JSONException;
import org.json.JSONObject;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;
import com.FT312D.utility.FT311UARTInterface;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.LexiconListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.cloud.util.ContactManager;
import com.iflytek.cloud.util.ContactManager.ContactListener;
import com.iflytek.speech.setting.IatSettings;
import com.iflytek.speech.util.ApkInstaller;
import com.iflytek.speech.util.FucUtil;
import com.iflytek.speech.util.JsonParser;
import com.iflytek.sunflower.FlowerCollector;

public class IatDemo extends Activity implements OnClickListener {
	private static String TAG = IatDemo.class.getSimpleName();
	// 语音听写对象
	private SpeechRecognizer mIat;
	// 语音听写UI
	private RecognizerDialog mIatDialog;
	// 用HashMap存储听写结果
	private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();

	private EditText mResultText;
	private Toast mToast;
	private SharedPreferences mSharedPreferences;
	// 引擎类型
	private String mEngineType = SpeechConstant.TYPE_CLOUD;
	// 语记安装助手类
	ApkInstaller mInstaller;
	
	//用于语音合成  add by Frank
	private SpeechSynthesizer mTts;

	// 默认发音人
	private String voicer = "xiaoai";
	// 情感
	private String emot= "";
	
	//用于存储命令词(此命令次为本项目自定义) add by Frank
	byte[] c = {0x01,0x02,0x03,0x04,0x15};
	
	//FT312D
	final int FORMAT_ASCII = 0;
	
	int inputFormat = FORMAT_ASCII;
	
	/* local variables */
	byte[] writeBuffer;
	byte[] readBuffer;
	char[] readBufferToChar;
	int[] actualNumBytes;

	int numBytes;
	byte count;
	byte status;
	byte writeIndex = 0;
	byte readIndex = 0;
	
	/* thread to read the data */
	public handler_thread handlerThread;

	/* declare a FT311 UART interface variable */
	public FT311UARTInterface uartInterface;
	
	private static boolean allowSpeechFalg = false;
	

	@SuppressLint("ShowToast")
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.iatdemo);

		initLayout();
		
		// 初始化识别无UI识别对象
		// 使用SpeechRecognizer对象，可根据回调消息自定义界面；
		mIat = SpeechRecognizer.createRecognizer(IatDemo.this, mInitListener);
		
		// 初始化语音合成对象
		mTts = SpeechSynthesizer.createSynthesizer(IatDemo.this, mTtsInitListener);
		
		// 初始化听写Dialog，如果只使用有UI听写功能，无需创建SpeechRecognizer
		// 使用UI听写功能，请根据sdk文件目录下的notice.txt,放置布局文件和图片资源
		mIatDialog = new RecognizerDialog(IatDemo.this, mInitListener);

		mSharedPreferences = getSharedPreferences(IatSettings.PREFER_NAME,
				Activity.MODE_PRIVATE);
		mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
		mResultText = ((EditText) findViewById(R.id.iat_text));
		mInstaller = new ApkInstaller(IatDemo.this);
	}

	/**
	 * 初始化Layout。
	 */
	private void initLayout() {
		findViewById(R.id.iat_recognize).setOnClickListener(IatDemo.this);
		findViewById(R.id.iat_upload_contacts).setOnClickListener(IatDemo.this);
		findViewById(R.id.iat_upload_userwords).setOnClickListener(IatDemo.this);
		findViewById(R.id.iat_stop).setOnClickListener(IatDemo.this);
		findViewById(R.id.iat_cancel).setOnClickListener(IatDemo.this);
		findViewById(R.id.image_iat_set).setOnClickListener(IatDemo.this);
		findViewById(R.id.write).setOnClickListener(IatDemo.this);
		
		mEngineType = SpeechConstant.TYPE_CLOUD;
		
		
		/* allocate buffer */
		writeBuffer = new byte[64];
		readBuffer = new byte[4096];
		readBufferToChar = new char[4096]; 
		actualNumBytes = new int[1];
		
		uartInterface = new FT311UARTInterface(IatDemo.this, null);

		handlerThread = new handler_thread(handler);
		
		handlerThread.start();
		
	}

	int ret = 0; // 函数调用返回值

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		// 进入参数设置页面
		case R.id.image_iat_set:
			Intent intents = new Intent(IatDemo.this, IatSettings.class);
			startActivity(intents);
			break;
		// 开始听写
		// 如何判断一次听写结束：OnResult isLast=true 或者 onError
		case R.id.iat_recognize:
			startSpeech();
			break;
		// 停止听写
		case R.id.iat_stop:
			mIat.stopListening();
			showTip("停止听写");
			break;
		// 取消听写
		case R.id.iat_cancel:
			mIat.cancel();
			showTip("取消听写");
			break;
		// 上传联系人
		case R.id.iat_upload_contacts:
			showTip(getString(R.string.text_upload_contacts));
			ContactManager mgr = ContactManager.createManager(IatDemo.this,
					mContactListener);
			mgr.asyncQueryAllContactsName();
			break;
		// 上传用户词表
		case R.id.iat_upload_userwords:
			showTip(getString(R.string.text_upload_userwords));
			String contents = FucUtil.readFile(IatDemo.this, "userwords","utf-8");
			mResultText.setText(contents);
			// 指定引擎类型
			mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
			mIat.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
			ret = mIat.updateLexicon("userword", contents, mLexiconListener);
			if (ret != ErrorCode.SUCCESS)
				showTip("上传热词失败,错误码：" + ret);
			break;
			
		case R.id.write:
			writeData("123");
			break;
		default:
			break;
		}
	}

	
	private void startSpeech(){
		
		mResultText.setText(null);// 清空显示内容
		mIatResults.clear();
		// 设置参数
		setParam();
		boolean isShowDialog = mSharedPreferences.getBoolean(
				getString(R.string.pref_key_iat_show), true);
		if (isShowDialog) {
			// 显示听写对话框
			mIatDialog.setListener(mRecognizerDialogListener);
			mIatDialog.show();
			showTip(getString(R.string.text_begin));
		} else {
			// 不显示听写对话框
			ret = mIat.startListening(mRecognizerListener);
			if (ret != ErrorCode.SUCCESS) {
				showTip("听写失败,错误码：" + ret);
			} else {
				showTip(getString(R.string.text_begin));
			}
		}
	}
	
	
	/**
	 * 初始化监听。
	 */
	private InitListener mTtsInitListener = new InitListener() {
		@Override
		public void onInit(int code) {
			Log.d(TAG, "InitListener init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
        		showTip("初始化失败,错误码："+code);
        	} else {
				// 初始化成功，之后可以调用startSpeaking方法
        		// 注：有的开发者在onCreate方法中创建完合成对象之后马上就调用startSpeaking进行合成，
        		// 正确的做法是将onCreate中的startSpeaking调用移至这里
			}		
		}
	};
	
	/**
	 * 初始化监听器。
	 */
	private InitListener mInitListener = new InitListener() {

		@Override
		public void onInit(int code) {
			Log.d(TAG, "SpeechRecognizer init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
				showTip("初始化失败，错误码：" + code);
			}
		}
	};

	/**
	 * 上传联系人/词表监听器。
	 */
	private LexiconListener mLexiconListener = new LexiconListener() {

		@Override
		public void onLexiconUpdated(String lexiconId, SpeechError error) {
			if (error != null) {
				showTip(error.toString());
			} else {
				showTip(getString(R.string.text_upload_success));
			}
		}
	};

	/**
	 * 听写监听器。
	 */
	private RecognizerListener mRecognizerListener = new RecognizerListener() {

		@Override
		public void onBeginOfSpeech() {
			// 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
			showTip("开始说话");
		}

		@Override
		public void onError(SpeechError error) {
			// Tips：
			// 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
			// 如果使用本地功能（语记）需要提示用户开启语记的录音权限。
			showTip(error.getPlainDescription(true));
		}

		@Override
		public void onEndOfSpeech() {
			// 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
			showTip("结束说话");
		}

		@Override
		public void onResult(RecognizerResult results, boolean isLast) {
			Log.d(TAG, results.getResultString());
			printResult(results);

			if (isLast) {
				// TODO 最后的结果
			}
		}

		@Override
		public void onVolumeChanged(int volume, byte[] data) {
			showTip("当前正在说话，音量大小：" + volume);
			Log.d(TAG, "返回音频数据："+data.length);
		}

		@Override
		public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
			// 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
			// 若使用本地能力，会话id为null
			//	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
			//		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
			//		Log.d(TAG, "session id =" + sid);
			//	}
		}
	};

	private void printResult(RecognizerResult results) {
		String text = JsonParser.parseIatResult(results.getResultString());

		String sn = null;
		// 读取json结果中的sn字段
		try {
			JSONObject resultJson = new JSONObject(results.getResultString());
			sn = resultJson.optString("sn");
		} catch (JSONException e) {
			e.printStackTrace();
		}

		mIatResults.put(sn, text);

		StringBuffer resultBuffer = new StringBuffer();
		for (String key : mIatResults.keySet()) {
			resultBuffer.append(mIatResults.get(key));
		}

		mResultText.setText(resultBuffer.toString());
		
		/*
		 * 判断是否为特定命令词
		 * add by Frank
		 */
		if( "你好".equals(resultBuffer.toString()) && allowSpeechFalg)
		{
			setTtsParam();
			mTts.startSpeaking("你好，我是机器人瑞宝", mTtsListener);
			//SendByBT(1);
			showTip("Forward");
		}else if( "你会唱歌吗".equals(resultBuffer.toString()) && allowSpeechFalg )
		{
			setTtsParam();
			mTts.startSpeaking("会的，我这就唱给你听呢", mTtsListener);
			//SendByBT(2);
			showTip("Backward");
		}else if( "向前滑行".equals(resultBuffer.toString()) )
		{
			//SendByBT(3);
			showTip("Slide Forward");
		}else if( "向后滑行".equals(resultBuffer.toString()) )
		{
			//SendByBT(4);
			showTip("Slide Backward");
		}else if( "停止".equals(resultBuffer.toString()) || "别走了".equals(resultBuffer.toString()) || "停下来".equals(resultBuffer.toString()) || "暂停".equals(resultBuffer.toString()) || "Stop".equals(resultBuffer.toString()) )
		{
			//SendByBT(5);
			showTip("Stop");
		}
		
		mResultText.setSelection(mResultText.length());
	}

	/*
	 * 将命令通过蓝牙发送出去
	 * add by Frank
	 */
	public void SendByBT(int theCmd)
	{
		//蓝牙连接输出流
		switch(theCmd)
		{
		case 1:
			
			try {
				OutputStream os = MainActivity._socket.getOutputStream();
				os.write(c[0]);
			} catch (IOException e) {} 
			
			break;
			
		case 2:
			
			try {
				OutputStream os = MainActivity._socket.getOutputStream();
				os.write(c[1]);
				
			} catch (IOException e) {}

			break;
		case 3:
			
			try {
				OutputStream os = MainActivity._socket.getOutputStream();
				os.write(c[2]);
			} catch (IOException e) {}
			
			break;
		case 4:
			
			try {
				OutputStream os = MainActivity._socket.getOutputStream();
				os.write(c[3]);
			} catch (IOException e) {}
			
			break;
		case 5:
			
			try {
				OutputStream os = MainActivity._socket.getOutputStream();
				os.write(c[4]);
			} catch (IOException e) {}
			
			break;
		}
	}
	
	
	/**
	 * 合成回调监听。
	 */
	private SynthesizerListener mTtsListener = new SynthesizerListener() {
		
		@Override
		public void onSpeakBegin() {
			showTip("开始播放");
		}

		@Override
		public void onSpeakPaused() {
			showTip("暂停播放");
		}

		@Override
		public void onSpeakResumed() {
			showTip("继续播放");
		}

		@Override
		public void onBufferProgress(int percent, int beginPos, int endPos,
				String info) {
			
		}

		@Override
		public void onSpeakProgress(int percent, int beginPos, int endPos) {
			// 播放进度
	
			
		}

		@Override
		public void onCompleted(SpeechError error) {
			if (error == null) {
				showTip("播放完成");
			} else if (error != null) {
				showTip(error.getPlainDescription(true));
			}
		}

		@Override
		public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
			// 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
			// 若使用本地能力，会话id为null
			//	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
			//		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
			//		Log.d(TAG, "session id =" + sid);
			//	}
		}
	};
	
	
	/**
	 * 听写UI监听器
	 */
	private RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
		public void onResult(RecognizerResult results, boolean isLast) {
			printResult(results);
		}

		/**
		 * 识别回调错误.
		 */
		public void onError(SpeechError error) {
			showTip(error.getPlainDescription(true));
		}

	};

	/**
	 * 获取联系人监听器。
	 */
	private ContactListener mContactListener = new ContactListener() {

		@Override
		public void onContactQueryFinish(final String contactInfos, boolean changeFlag) {
			// 注：实际应用中除第一次上传之外，之后应该通过changeFlag判断是否需要上传，否则会造成不必要的流量.
			// 每当联系人发生变化，该接口都将会被回调，可通过ContactManager.destroy()销毁对象，解除回调。
			// if(changeFlag) {
			// 指定引擎类型
			runOnUiThread(new Runnable() {
				public void run() {
					mResultText.setText(contactInfos);
				}
			});
			
			mIat.setParameter(SpeechConstant.ENGINE_TYPE,SpeechConstant.TYPE_CLOUD);
			mIat.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
			ret = mIat.updateLexicon("contact", contactInfos, mLexiconListener);
			if (ret != ErrorCode.SUCCESS) {
				showTip("上传联系人失败：" + ret);
			}
		}
	};

	private void showTip(final String str) {
		mToast.setText(str);
		mToast.show();
	}
	
	
	
	/**
	 * 参数设置
	 * @param param
	 * @return 
	 */
	private void setTtsParam(){
		// 清空参数
		mTts.setParameter(SpeechConstant.PARAMS, null);
		// 根据合成引擎设置相应参数
		if(mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
			mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
			// 设置在线合成发音人
			mTts.setParameter(SpeechConstant.VOICE_NAME, voicer);
			if(!"neutral".equals(emot)){
				// 当前仅发音人“小艾”支持设置情感
				// “小艾”发音人需要付费使用，具体请联系：msp_support@iflytek.com
				mTts.setParameter(SpeechConstant.EMOT, emot);
			}
			//设置合成语速
			mTts.setParameter(SpeechConstant.SPEED, mSharedPreferences.getString("speed_preference", "50"));
			//设置合成音调
			mTts.setParameter(SpeechConstant.PITCH, mSharedPreferences.getString("pitch_preference", "50"));
			//设置合成音量
			mTts.setParameter(SpeechConstant.VOLUME, mSharedPreferences.getString("volume_preference", "50"));
		}else {
			mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
			// 设置本地合成发音人 voicer为空，默认通过语记界面指定发音人。
			mTts.setParameter(SpeechConstant.VOICE_NAME, "");
			/**
			 * TODO 本地合成不设置语速、音调、音量，默认使用语记设置
			 * 开发者如需自定义参数，请参考在线合成参数设置
			 */
		}
		//设置播放器音频流类型
		mTts.setParameter(SpeechConstant.STREAM_TYPE, mSharedPreferences.getString("stream_preference", "3"));
		// 设置播放合成音频打断音乐播放，默认为true
		mTts.setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "true");
		
		// 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
		// 注：AUDIO_FORMAT参数语记需要更新版本才能生效
		mTts.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
		mTts.setParameter(SpeechConstant.TTS_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/msc/tts.wav");
	}
	

	/**
	 * 参数设置
	 * 
	 * @param param
	 * @return
	 */
	public void setParam() {
		// 清空参数
		mIat.setParameter(SpeechConstant.PARAMS, null);

		// 设置听写引擎
		mIat.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
		// 设置返回结果格式
		mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");

		String lag = mSharedPreferences.getString("iat_language_preference",
				"mandarin");
		if (lag.equals("en_us")) {
			// 设置语言
			mIat.setParameter(SpeechConstant.LANGUAGE, "en_us");
		} else {
			// 设置语言
			mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
			// 设置语言区域
			mIat.setParameter(SpeechConstant.ACCENT, lag);
		}

		// 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
		mIat.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("iat_vadbos_preference", "4000"));
		
		// 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
		mIat.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("iat_vadeos_preference", "1000"));
		
		// 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
		mIat.setParameter(SpeechConstant.ASR_PTT, mSharedPreferences.getString("iat_punc_preference", "1"));
		
		// 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
		// 注：AUDIO_FORMAT参数语记需要更新版本才能生效
		mIat.setParameter(SpeechConstant.AUDIO_FORMAT,"wav");
		mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/msc/iat.wav");
		
		// 设置听写结果是否结果动态修正，为“1”则在听写过程中动态递增地返回结果，否则只在听写结束之后返回最终结果
		// 注：该参数暂时只对在线听写有效
		mIat.setParameter(SpeechConstant.ASR_DWA, mSharedPreferences.getString("iat_dwa_preference", "0"));
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		// 退出时释放连接
		mIat.cancel();
		mIat.destroy();
		
		mTts.stopSpeaking();
		// 退出时释放连接
		mTts.destroy();
		
		//FT312D
		uartInterface.DestroyAccessory(true);
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void onResume() {
		// 开放统计 移动数据统计分析
		FlowerCollector.onResume(IatDemo.this);
		FlowerCollector.onPageStart(TAG);
		super.onResume();
		//FT312D
		uartInterface.ResumeAccessory();
		uartInterface.SetConfig(9600,(byte)8,(byte)1,(byte)0,(byte)0);
	}

	@Override
	protected void onPause() {
		// 开放统计 移动数据统计分析
		FlowerCollector.onPageEnd(TAG);
		FlowerCollector.onPause(IatDemo.this);
		super.onPause();
	}

	
	Thread thread = new Thread(new Runnable() {
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			
		}
	});
	
	
	
	
	/*
	 * 
	 * **********************************************************************************************************************
	 * 
	 *                                以下为FT312D操作
	 * 
	 * **********************************************************************************************************************
	 * 
	 */
	
	 /*
     * 以十进制写入数据
     * Modified by frank、
     * 
     */
    public void writeData(String string)
    {
    	String srcStr = string;    	
    	String destStr = "";
    	
    	destStr = srcStr;

		numBytes = destStr.length();
		for (int i = 0; i < numBytes; i++) {
			writeBuffer[i] = (byte)destStr.charAt(i);
		}
		uartInterface.SendData(numBytes, writeBuffer);
		
    }
	
	
	
	//@Override
		public void onHomePressed() {
			onBackPressed();
		}	

		public void onBackPressed() {
		    super.onBackPressed();
		}
	
	/*
	 * 
	 * 接收USB传输过来的数据，更新UI
	 * 
	 */
	final Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			
			for(int i=0; i<actualNumBytes[0]; i++)
			{
				readBufferToChar[i] = (char)readBuffer[i];
			}	
			
			String s1 = new String(readBufferToChar).trim();
			
			if("ok".equals(s1)){
			 	allowSpeechFalg = true;
			 	showTip(s1+"true");
			 	startSpeech();
			}else {
				allowSpeechFalg = false;
				showTip(s1+"false");
			}
			
		}
	};
	
	
	/*
	 * 
	 * 接收USB数据线程
	 * 
	 * 
	 */
	private class handler_thread extends Thread {
		Handler mHandler;

		/* constructor */
		handler_thread(Handler h) {
			mHandler = h;
		}

		public void run() {
			Message msg;

			while (true) {
	
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
				}

				status = uartInterface.ReadData(4096, readBuffer,actualNumBytes);

				if (status == 0x00 && actualNumBytes[0] > 0) {
					msg = mHandler.obtainMessage();
					mHandler.sendMessage(msg);
				}

			}
		}
	}
		

}
