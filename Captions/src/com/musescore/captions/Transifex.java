package com.musescore.captions;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.api.client.util.Base64;

public class Transifex {
	
	private String projectSlug;
	private String encoding;
	public Date referenceDate;
	private JSONArray jsonArrayResources;
	
	public Transifex(String projectSlug, String auth){
		this.projectSlug = projectSlug;
		this.encoding = Base64.encodeBase64String(auth.getBytes());
	}
	
	//connect to Transifex, get response
	private String getResponse(String urlPart){
		try {
			URL url = new URL ("https://www.transifex.com/api/2/project/" + urlPart);
			HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);
            connection.setRequestProperty  ("Authorization", "Basic " + encoding);
            InputStream content = (InputStream)connection.getInputStream();
            //UTF8 Charset for non-ASCII chars
            BufferedReader in = new BufferedReader (new InputStreamReader(content, "UTF8"));
            String inputText = "";
            String line;
            while ((line = in.readLine()) != null) {
               inputText += line;
            }
            return inputText;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	    
    //Gets the slug from each resource
    public List<String> getAllResourceSlugs() throws JSONException {
    	String inputText = getResponse(projectSlug+"/resources/");
    	jsonArrayResources = new JSONArray(inputText);
    	List<String> slugList = new ArrayList<String>();
    	for(int i = 0; i<jsonArrayResources.length(); i++){
			JSONObject o = (JSONObject)jsonArrayResources.get(i);
			slugList.add(o.getString("slug"));
    	}
    	return slugList;
    }
    
    //Gets the file type of the resource (srt/sbv) Not needed anymore
    public String getFileType(String resourceSlug) throws JSONException{
    	String inputText = getResponse(projectSlug+"/resource/"+resourceSlug+"/?details");  
        JSONObject object;
		object = new JSONObject(inputText);        
    	return object.getString("i18n_type");
    }
    
    //Get the list of a resource languages that need an update
    public List<String> getDepricatedLanguages(String resourceSlug, Date lastRuntime) throws JSONException, ParseException {
    	
    	List<String> languageCodes = new ArrayList<String>();
    	
    	//Get available languages
    	String inputText = getResponse(projectSlug+"/resource/"+resourceSlug+"/?details");  
        JSONObject object = new JSONObject(inputText);
        JSONArray languageArray = (JSONArray)object.get("available_languages");
        
        //Gets the details of all languages
        inputText = getResponse(projectSlug+"/resource/" +resourceSlug+ "/stats/");
        JSONObject languageDetails = new JSONObject(inputText);
        
        //Checks if language needs an update (when modified after lastRunTime or when it's the first time)
        for(int j = 0; j < languageArray.length(); j++){
        	JSONObject languageObject = languageArray.getJSONObject(j);
        	String languageCode = languageObject.getString("code");
        	String lastUpdate = languageDetails.getJSONObject(languageCode).getString("last_update");
        	
        	//Put lastUpdate String into a Date object
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
    		Date lastModifiedDate = df.parse(lastUpdate);		
    		
    		//Check if lastModifiedDate is younger than lastRuntime date (or first time running when lastRuntime is null)   
    		//if true, an update is needed
        	if(lastRuntime == null || lastModifiedDate.after(lastRuntime)){       		
        		//check if minimum 1 word is translated
        		String s = projectSlug + "/resource/" + resourceSlug +"/stats/" + languageCode + "/";
        		JSONObject languageInfo = new JSONObject(getResponse(s));
        		
        		//TODO: What about ca@valencia language?
        		//If YouTube adds this languagecode, remove in line below: && !languageCode.contains("valencia")
        		if((int)languageInfo.get("translated_words") != 0 && !languageCode.contains("valencia")){
        			languageCodes.add(languageCode);
        		}
        	}
        }
        
        return languageCodes;        
    }

    //Gets the content of a caption
    public String getCaption(String resourceSlug, String languageCode) throws JSONException{
		String s = projectSlug + "/resource/" + resourceSlug +"/translation/" + languageCode + "/";
		JSONObject caption = new JSONObject(getResponse(s));
		return (String)caption.get("content");	
    }
    
    public List<String> getAllLanguages(String resourceSlug) throws JSONException {
    	List<String> allLanguages = new ArrayList<String>();
    	
    	String inputText = getResponse(projectSlug+"/resource/"+resourceSlug+"/?details");  
        JSONObject object = new JSONObject(inputText);
        //get the languages for the resource
        JSONArray languageArray = (JSONArray)object.get("available_languages");

        for(int j = 0; j < languageArray.length(); j++){
        	JSONObject language = languageArray.getJSONObject(j);
        	allLanguages.add(language.getString("code"));  	
        }
        
        return allLanguages;
    }
    
    public String getTranslation(String resourceSlug, String languageCode, String text) throws JSONException{
    	String inputText = getResponse(projectSlug+"/resource/"+resourceSlug+"/translation/" + languageCode + "/strings/");
    	JSONArray translationStrings = new JSONArray(inputText);
    	for(int k = 0; k < translationStrings.length(); k++){
    		JSONObject translationStr = translationStrings.getJSONObject(k);
    		if(translationStr.getString("source_string").contains(text) && !translationStr.getString("translation").isEmpty()){
    			return translationStr.getString("translation");
    		}
    	}
    	return null;
    }
        
}