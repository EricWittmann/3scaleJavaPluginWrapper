package net.threescale.service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import threescale.v3.api.AuthorizeResponse;
import threescale.v3.api.ParameterMap;
import threescale.v3.api.ServerError;
import threescale.v3.api.ServiceApi;
import threescale.v3.api.impl.ServiceApiDriver;

/**
 * 
 * @author tomcorcoran
 * Implementation of the 3scale Java Plugin Wrapper
 *
 */
@Service
public class PluginServiceImpl implements PluginService{
	private Map <String, ThreeScaleMapping> mappings = null;
	
	/** Previous Authorization results **/
	private Map <String, Boolean> authorizations = new HashMap<String, Boolean>();
	
	
	/**
	 * Constructor where mappings is initialized with the mappings you defined on the API->Integration screen 
	 * on 3scale. In future enhancements, we will pull in these using the 3scale API. 
	 * We provide 2 example mappings - a literal and a Regex pattern of a path. Note we don't advocate adding 
	 * a lot of Regex paths as it can add latency. If you have a lot of paths, we recommend splitting this 
	 * service up into multiple components with a small number of paths in each. That way it doesn't have to 
	 * loop and match multiple paths at runtime.
	 */
	public PluginServiceImpl() {
		super();
		mappings = new HashMap<String, ThreeScaleMapping>();
		
		String regexUrlPattern = "/managed/catalog/product";
		Pattern pattern = Pattern.compile(regexUrlPattern);
		mappings.put(regexUrlPattern, new ThreeScaleMapping("GET", "123456", "getProducts", pattern));
		
		regexUrlPattern = "/managed/catalog/product/(\\w+)";
		pattern = Pattern.compile(regexUrlPattern);
		mappings.put(regexUrlPattern, new ThreeScaleMapping("GET", "987654", "getProduct", pattern));

	}

	
	/**
	 * Main Implementing method. If the cache (authorizations) has stored 'true' against userKey:MetricOrMethod, make an
	 * asynchronous call to 3scale, otherwise make a synchronous call. 
	 */
	@Override
	public AuthorizeResponse authRep(String userKey, String requestPath) {
		
		//First see is there an exact match from the path to the pattern
    	ThreeScaleMapping mappingFound = mappings.get(requestPath);
    	
		//Next, in the case of paths with embedded path variables, see is there a Regex match to one of our configured patterns
    	if (mappingFound == null){
    		for (ThreeScaleMapping mapping: mappings.values()){
    			Matcher matcher = mapping.getPattern().matcher(requestPath);
    		    if (matcher.matches()) {
    		    	mappingFound = mapping;
    		    	break;
    			}
    		}
    	}
    	if (mappingFound == null){
    		return null;
    	}
    	
		AuthorizeResponse resp = null;
		
		String key = userKey+mappingFound.getMetricOrMethod();
		Boolean auth = authorizations.get(key);
		if (auth!=null && (auth==true)){
			Thread asyncAuth = new ASyncAuth(userKey, requestPath, mappingFound);
			asyncAuth.start();
		}
		else{

			return getSyncAuthResponse(userKey, requestPath, mappingFound);
			
		}
    	return resp;
	}
	
	/**
	 * @author tomcorcoran
	 * Used for asynchronous calls.
	 *
	 */
	class ASyncAuth extends Thread {
		String userKey, requestPath;
		ThreeScaleMapping mapping;
		ASyncAuth(String uKey, String rPath, ThreeScaleMapping mping){
			userKey = uKey;
			requestPath = rPath;
			mapping = mping;
		}
	    public void run(){
	    	AuthorizeResponse resp = getAuthResponse(userKey, requestPath, mapping);
	    }
	    
	  }
	
	
    /**
     * Used for synchronous calls
     */
	private AuthorizeResponse getSyncAuthResponse(String userKey, String requestPath, ThreeScaleMapping mapping){
		return getAuthResponse(userKey, requestPath, mapping);
	}
	
	private AuthorizeResponse getAuthResponse(String userKey, String requestPath, ThreeScaleMapping mapping){
 

    	AuthorizeResponse authorizeResponse = null;
    	ServiceApi serviceApi = new ServiceApiDriver(PluginService.PROVIDER_KEY);
    	
    	ParameterMap paramMap = buildParameterMap(mapping.getServiceId(), userKey, mapping.getMetricOrMethod(), 1);

    	try {
    		authorizeResponse = serviceApi.authrep(paramMap);
		} catch (ServerError e) {
			e.printStackTrace();
		}
    	
    	authorizations.put(userKey+mapping.getMetricOrMethod(), authorizeResponse.success());
    	
       	return authorizeResponse;
		
	}
	
	
    private ParameterMap buildParameterMap(String serviceId, String userKey, String metric, Integer incrementBy){
    	ParameterMap params = new ParameterMap();
		params.add("service_id", serviceId);
		params.add("user_key", userKey);		
    			
		ParameterMap usage = new ParameterMap();
		usage.add(metric, incrementBy.toString());
		params.add("usage", usage);
		
		return params;
    }

    
}
