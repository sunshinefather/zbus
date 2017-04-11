package io.zbus.performance.disk;

import java.io.File;

import io.zbus.mq.disk.DiskMessage;
import io.zbus.mq.disk.Index;
import io.zbus.mq.disk.QueueWriter;

public class QueueWriterPerf {
	
	public static void main(String[] args) throws Exception { 
		Index index = new Index(new File("C:/tmp/MyMQ")); 
		QueueWriter q = new QueueWriter(index);
		
		long start = System.currentTimeMillis();
		
		for(int i=0;i<20000000;i++){
			DiskMessage data = new DiskMessage();
			data.body = new byte[102400]; 
			if(i%100==0){
				data.tag = String.format("abc.%d", i/100);
			}
			q.write(data);
			
			if((i+1)%1000 == 0){
				long end = System.currentTimeMillis(); 
				System.out.format("%.4f M/s %d\n", 1000*data.size()*1000.0/1024.0/1024.0/(end-start),(i+1));
				start = System.currentTimeMillis();
			}
		}
		
		index.close();
	}
	
}
