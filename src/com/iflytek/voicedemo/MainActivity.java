package com.iflytek.voicedemo;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {

	private Toast mToast;
	
	//add for BT
	private BluetoothAdapter _bluetooth = BluetoothAdapter.getDefaultAdapter();    //获取本地蓝牙适配器，即蓝牙设备
	BluetoothDevice _device = null;     //蓝牙设备
    static BluetoothSocket _socket = null;      //蓝牙通信socket
    private final static int REQUEST_CONNECT_DEVICE = 1;    //宏定义查询设备句柄
	private final static String MY_UUID = "00001101-0000-1000-8000-00805F9B34FB";   //SPP服务UUID号
	private InputStream is;    //输入流，用来接收蓝牙数据
	boolean bRun = true;
	
	
	@SuppressLint("ShowToast")
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);
		
		mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
		SimpleAdapter listitemAdapter = new SimpleAdapter();
		((ListView) findViewById(R.id.listview_main)).setAdapter(listitemAdapter);
		
		//如果打开本地蓝牙设备不成功，提示信息，结束程序
        if (_bluetooth == null){
        	Toast.makeText(this, "无法打开手机蓝牙，请确认手机是否有蓝牙功能！", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // 设置设备可以被搜索  
       new Thread(){
    	   public void run(){
    		   if(_bluetooth.isEnabled()==false){
        		_bluetooth.enable();
    		   }
    	   }   	   
       }.start();   
	}

	@Override
	public void onClick(View view) {
		int tag = Integer.parseInt(view.getTag().toString());
		Intent intent = null;
		switch (tag) {
		case 0:
			// 语音转写
			intent = new Intent(MainActivity.this, IatDemo.class);
			break;
		case 1:
			// 语法识别
			intent = new Intent(MainActivity.this, AsrDemo.class);
			break;
		case 2:
			// 语义理解
			intent = new Intent(MainActivity.this, UnderstanderDemo.class);
			break;
		case 3:
			// 语音合成
			intent = new Intent(MainActivity.this, TtsDemo.class);
			break;
		case 4:
			// 语音评测
			intent = new Intent(MainActivity.this, IseDemo.class);
			break;
		case 5:
			// 唤醒
			showTip("敬请期待！");
			break;
		case 6:
			// 声纹
		default:
			showTip("在IsvDemo中哦，为了代码简洁，就不放在一起啦，^_^");
			break;
		}
		
		if (intent != null) {
			startActivity(intent);
		}
	}
	
	
	//接收活动结果，响应startActivityForResult()
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	switch(requestCode){
    	case REQUEST_CONNECT_DEVICE:     //连接结果，由DeviceListActivity设置返回
    		// 响应返回结果
            if (resultCode == Activity.RESULT_OK) {   //连接成功，由DeviceListActivity设置返回
                // MAC地址，由DeviceListActivity设置返回
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // 得到蓝牙设备句柄      
                _device = _bluetooth.getRemoteDevice(address);
 
                // 用服务号得到socket
                try{
                	_socket = _device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID));
                }catch(IOException e){
                	Toast.makeText(this, "连接失败！", Toast.LENGTH_SHORT).show();
                }
                //连接socket
            	Button btn = (Button) findViewById(R.id.bConnect);
                try{
                	_socket.connect();
                	Toast.makeText(this, "连接"+_device.getName()+"成功！", Toast.LENGTH_SHORT).show();
                	btn.setText("断开连接");
                }catch(IOException e){
                	try{
                		Toast.makeText(this, "连接失败！", Toast.LENGTH_SHORT).show();
                		_socket.close();
                		_socket = null;
                	}catch(IOException ee){
                		Toast.makeText(this, "连接失败！", Toast.LENGTH_SHORT).show();
                	}
                	
                	return;
                }
                
            }
    		break;
    	default:break;
    	}
    }
    
  //关闭程序掉用处理部分
    public void onDestroy(){
    	super.onDestroy();
    	if(_socket!=null)  //关闭连接socket
    	try{
    		_socket.close();
    	}catch(IOException e){}
    //	_bluetooth.disable();  //关闭蓝牙服务
    }
    
  //连接按键响应函数
    public void onConnectButtonClicked(View v){ 
    	if(_bluetooth.isEnabled()==false){  //如果蓝牙服务不可用则提示
    		Toast.makeText(this, " 打开蓝牙中...", Toast.LENGTH_LONG).show();
    		return;
    	}
    	
    	
        //如未连接设备则打开DeviceListActivity进行设备搜索
    	Button btn = (Button) findViewById(R.id.bConnect);
    	if(_socket==null){
    		Intent serverIntent = new Intent(this, DeviceListActivity.class); //跳转程序设置
    		startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);  //设置返回宏定义
    	}
    	else{
    		 //关闭连接socket
    	    try{
    	    	
    	    	is.close();
    	    	_socket.close();
    	    	_socket = null;
    	    	bRun = false;
    	    	btn.setText("连接lanya");
    	    }catch(IOException e){}   
    	}
    	return;
    }

	// Menu 列表
	String items[] = { "立刻体验语音听写", "立刻体验语法识别", "立刻体验语义理解", "立刻体验语音合成",
			"立刻体验语音评测", "立刻体验语音唤醒", "立刻体验声纹密码" };

	private class SimpleAdapter extends BaseAdapter {
		public View getView(int position, View convertView, ViewGroup parent) {
			if (null == convertView) {
				LayoutInflater factory = LayoutInflater.from(MainActivity.this);
				View mView = factory.inflate(R.layout.list_items, null);
				convertView = mView;
			}
			
			Button btn = (Button) convertView.findViewById(R.id.btn);
			btn.setOnClickListener(MainActivity.this);
			btn.setTag(position);
			btn.setText(items[position]);
			
			return convertView;
		}

		@Override
		public int getCount() {
			return items.length;
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}
	}

	private void showTip(final String str) {
		mToast.setText(str);
		mToast.show();
	}
	
}
