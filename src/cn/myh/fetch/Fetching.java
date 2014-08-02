package cn.myh.fetch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.hadoop.mapred.TextOutputFormat;

import cn.myh.bean.UrlData;


public class Fetching implements Mapper<Text, UrlData, Text, UrlData>
	, Reducer<Text, UrlData, Text, UrlData>{
		
		ArrayList<String> urls=new ArrayList<String>();
		
	@Override
	public void map(Text arg0, UrlData arg1,
			OutputCollector<Text, UrlData> arg2, Reporter arg3)
			throws IOException {
		// TODO Auto-generated method stub
		if(arg1.getStatus()==UrlData.STATUS_DB_UNFETCHED) {
			arg1.setStatus(UrlData.STATUS_DB_READYTOFETCH);
			arg2.collect(arg0, arg1);
			urls=HerfMatch.FindURL(arg0.toString());
			if(urls==null)
				return;
			for(int i=0;i<urls.size();i++) {
				UrlData tmp=new UrlData();
				String url=urls.get(i);
				tmp.set(arg1);
				tmp.setStatus(UrlData.STATUS_DB_UNFETCHED);
				tmp.setlastFetchTime(System.currentTimeMillis());
				arg2.collect(new Text(url), tmp);
			}
		}else {
			arg2.collect(arg0, arg1);
		}
	}

	@Override
	public void configure(JobConf arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void reduce(Text arg0, Iterator<UrlData> arg1,
			OutputCollector<Text, UrlData> arg2, Reporter arg3)
			throws IOException {
		// TODO Auto-generated method stub
		UrlData data=new UrlData();
		while(arg1.hasNext()) {
			data.set(arg1.next());
		}
		arg2.collect(arg0, data);
	}
	
	/**
	 * @param args
	 * @throws IOException 
	 * @throws IOException 
	 */
	public void fetch(int d) throws IOException {
		int depth=d;
		JobClient client=new JobClient();
		JobConf conf=new JobConf(Fetching.class);
		/***
		 * ��һ��map-reduce���򣬴洢������ȷ�����tmp�����tmp�޸�����Ϊdb����DB�е�url��data�ı�־�޸�ΪSTATUS_DB_FETCHED
		 */
		conf.setJobName("updating db");
		String current=System.getProperty("user.dir")+"/crawl";
		Path fetchlist=new Path(current+"/fetch_list");
		Path tmp=new Path(current+"/tmp");
		Path db=new Path(current+"/db");
		Path stored=new Path(current+"/stored_web/"+new Date(System.currentTimeMillis()));
		//����reduce�������ʽ
		conf.setOutputKeyClass(Text.class);
		conf.setOutputValueClass(UrlData.class);
		
		//��������������ʽ
		conf.setInputFormat(SequenceFileInputFormat.class);
		//����reduce�����ʽ
		conf.setOutputFormat(SequenceFileOutputFormat.class);
		
		if(!FileSystem.get(conf).exists(fetchlist))
			FileSystem.get(conf).mkdirs(fetchlist);
		if(!FileSystem.get(conf).exists(db))
			FileSystem.get(conf).mkdirs(db);
	   
		
		/** 
         * InputFormat����map-reduce�ж�job�����붨�� 
         * setInputPaths():Ϊmap-reduce job����·��������Ϊ�����б� 
         * setInputPath()��Ϊmap-reduce job����·��������Ϊ����б� 
         */  
		//���������ļ���·��������ʹһ���ļ���һ��·����һ��ͨ��������Ա����ö����Ӷ��·�� 
		FileInputFormat.addInputPath(conf, db);
		FileInputFormat.addInputPath(conf, fetchlist);
		
		if(FileSystem.get(conf).exists(tmp))
			FileSystem.get(conf).delete(tmp, true);
		
		//��������ļ���·������job����ǰ��·����Ӧ�ô���
		FileOutputFormat.setOutputPath(conf, tmp);
		
		//����Mapper��Ĭ��ΪIdentityMapper 
		conf.setMapperClass(Update.class);
		conf.setReducerClass(Update.class);
		
		client.setConf(conf);
		try {
			JobClient.runJob(conf);
			if(FileSystem.get(conf).exists(db))
				FileSystem.get(conf).delete(db, true);
			FileSystem.get(conf).rename(tmp, db);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
//*************************************************************************		

	/***
	 * �ڶ���map-reduce������ȡ���Ϊdepth�Ĵ�fetchlist�ж�ȡ���������д��fetchlist��
	 */
		for(int i=0;i<depth;i++) {
			conf=new JobConf(Fetching.class);
			conf.setJobName("updating fetch list");
			
			//����Reducer�������key-value�Եĸ�ʽ 
			conf.setOutputKeyClass(Text.class);
			conf.setOutputValueClass(UrlData.class);
			
			conf.setInputFormat(SequenceFileInputFormat.class);
			conf.setOutputFormat(SequenceFileOutputFormat.class);
			
			FileInputFormat.setInputPaths(conf, fetchlist);
			if(FileSystem.get(conf).exists(tmp))
				FileSystem.get(conf).delete(tmp, true);
			
			FileOutputFormat.setOutputPath(conf, tmp);
			
			conf.setMapperClass(Fetching.class);
			conf.setReducerClass(Fetching.class);
			
			client.setConf(conf);
			try {
				JobClient.runJob(conf);
				if(FileSystem.get(conf).exists(fetchlist))
					FileSystem.get(conf).delete(fetchlist, true);
				FileSystem.get(conf).rename(tmp, fetchlist);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		conf=new JobConf(Fetching.class);
		conf.setJobName("crawling...");
		
		conf.setOutputKeyClass(Text.class);
		conf.setOutputValueClass(Text.class);
		
		conf.setInputFormat(SequenceFileInputFormat.class);
		conf.setOutputFormat(TextOutputFormat.class);
		
		
		FileInputFormat.setInputPaths(conf, fetchlist);
		FileOutputFormat.setOutputPath(conf, stored);
		
		conf.setMapperClass(View.class);
		conf.setReducerClass(View.class);
		
		client.setConf(conf);
		try {
			JobClient.runJob(conf);
			if(FileSystem.get(conf).exists(fetchlist))
				FileSystem.get(conf).delete(fetchlist, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		int depth=3;
		JobClient client=new JobClient();
		JobConf conf=new JobConf(Fetching.class);
		conf.setJobName("updating db");
		
		String current=System.getProperty("user.dir")+"/crawl";
		Path fetchlist=new Path(current+"/fetch_list");
		Path tmp=new Path(current+"/tmp");
		Path db=new Path(current+"/db");
		Path stored=new Path(current+"/stored_web/"+new Date(System.currentTimeMillis()));
		
		conf.setOutputKeyClass(Text.class);
		conf.setOutputValueClass(UrlData.class);
		
		conf.setInputFormat(SequenceFileInputFormat.class);
		conf.setOutputFormat(SequenceFileOutputFormat.class);
		
		if(!FileSystem.get(conf).exists(fetchlist))
			FileSystem.get(conf).mkdirs(fetchlist);
		if(!FileSystem.get(conf).exists(db))
			FileSystem.get(conf).mkdirs(db);
		
		FileInputFormat.addInputPath(conf, db);
		FileInputFormat.addInputPath(conf, fetchlist);
		if(FileSystem.get(conf).exists(tmp))
			FileSystem.get(conf).delete(tmp, true);
		FileOutputFormat.setOutputPath(conf, tmp);
		
		conf.setMapperClass(Update.class);
		conf.setReducerClass(Update.class);
		
		client.setConf(conf);
		try {
			JobClient.runJob(conf);
			if(FileSystem.get(conf).exists(db))
				FileSystem.get(conf).delete(db, true);
			FileSystem.get(conf).rename(tmp, db);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		for(int i=0;i<depth;i++) {
			conf=new JobConf(Fetching.class);
			conf.setJobName("updating fetch list");
			conf.setOutputKeyClass(Text.class);
			conf.setOutputValueClass(UrlData.class);
			
			conf.setInputFormat(SequenceFileInputFormat.class);
			conf.setOutputFormat(SequenceFileOutputFormat.class);
			
			FileInputFormat.setInputPaths(conf, fetchlist);
			if(FileSystem.get(conf).exists(tmp))
				FileSystem.get(conf).delete(tmp, true);
			FileOutputFormat.setOutputPath(conf, tmp);
			
			conf.setMapperClass(Fetching.class);
			conf.setReducerClass(Fetching.class);
			
			client.setConf(conf);
			try {
				JobClient.runJob(conf);
				if(FileSystem.get(conf).exists(fetchlist))
					FileSystem.get(conf).delete(fetchlist, true);
				FileSystem.get(conf).rename(tmp, fetchlist);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		conf=new JobConf(Fetching.class);
		conf.setJobName("crawling...");
		
		conf.setOutputKeyClass(Text.class);
		conf.setOutputValueClass(Text.class);
		
		conf.setInputFormat(SequenceFileInputFormat.class);
		conf.setOutputFormat(TextOutputFormat.class);
		
		FileInputFormat.setInputPaths(conf, fetchlist);
		FileOutputFormat.setOutputPath(conf, stored);
		
		conf.setMapperClass(View.class);
		conf.setReducerClass(View.class);
		
		client.setConf(conf);
		try {
			JobClient.runJob(conf);
			if(FileSystem.get(conf).exists(fetchlist))
				FileSystem.get(conf).delete(fetchlist, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
