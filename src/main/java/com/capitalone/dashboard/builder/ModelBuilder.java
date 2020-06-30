package com.capitalone.dashboard.builder;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import com.capitalone.dashboard.model.BaseModel;
import com.capitalone.dashboard.model.Chart;
import com.capitalone.dashboard.model.Repo;
import com.capitalone.dashboard.model.Version;

/**
 * Application configuration and bootstrap
 */
public class ModelBuilder {
	static SimpleDateFormat format = new SimpleDateFormat("EEE MMM dd hh:mm:ss yyyy");

    public static BaseModel createModelObject(Class clazz, Object[] args) throws ParseException {
        if(clazz.equals(Chart.class)) {
        	long millis = format.parse(args[0].toString()).getTime();
        	return new Chart(millis, args[1].toString(), args[2].toString(), args[3].toString(), args[4].toString());
        } else if(clazz.equals(Version.class)) {
        	return new Version(args[0].toString(), args[1].toString(), args[2].toString(), args[3].toString());
        } else if(clazz.equals(Repo.class)) {
	    	return new Repo(args[0].toString(), args[1].toString());
		} /*
			 * else if(clazz.equals(Release.class)) { return new Release(args[0].toString(),
			 * args[1].toString(), args[2].toString(), args[3].toString(),
			 * args[4].toString()); }
			 */
        
        return null;
    }	
}