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

import com.google.api.client.util.Base64;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Transifex {
	
	private String projectSlug;
	private String encoding;
	public Date referenceDate;
	private JsonArray jsonArrayResources;
	
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
    public List<String> getAllResourceSlugs() {
    	String inputText = getResponse(projectSlug+"/resources/");
    	jsonArrayResources =  new JsonParser().parse(inputText).getAsJsonArray();
    	List<String> slugList = new ArrayList<String>();
    	for(int i = 0; i<jsonArrayResources.size(); i++){
			JsonObject o = jsonArrayResources.get(i).getAsJsonObject();
			slugList.add(o.get("slug").getAsString());
    	}
    	return slugList;
    }
    
    //Get the list of a resource languages that need an update
    public List<String> getDepricatedLanguages(String resourceSlug, Date lastRuntime) throws ParseException {
    	
    	List<String> languageCodes = new ArrayList<String>();
    	
    	//Get available languages
    	String inputText = getResponse(projectSlug+"/resource/"+resourceSlug+"/?details");  
        JsonObject object = new JsonParser().parse(inputText).getAsJsonObject();
        JsonArray languageArray = object.get("available_languages").getAsJsonArray();
        
        //Gets the details of all languages
        inputText = getResponse(projectSlug+"/resource/" +resourceSlug+ "/stats/");
        JsonObject languageDetails = new JsonParser().parse(inputText).getAsJsonObject();
        
        //Checks if language needs an update (when modified after lastRunTime or when it's the first time)
        for(int j = 0; j < languageArray.size(); j++){
        	JsonObject languageObject = languageArray.get(j).getAsJsonObject();
        	String languageCode = languageObject.get("code").getAsString();
        	JsonObject jsonobj = languageDetails.get(languageCode).getAsJsonObject();
        	JsonElement js = jsonobj.get("last_update");
        	String lastUpdate = js.getAsString();
        	
        	//Put lastUpdate String into a Date object
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
    		Date lastModifiedDate = df.parse(lastUpdate);		
    		
    		//Check if lastModifiedDate is younger than lastRuntime date (or first time running when lastRuntime is null)   
    		//if true, an update is needed
        	if(lastRuntime == null || lastModifiedDate.after(lastRuntime)){       		
        		//check if minimum 1 word is translated
        		String s = projectSlug + "/resource/" + resourceSlug +"/stats/" + languageCode + "/";
        		JsonObject languageInfo = new JsonParser().parse(getResponse(s)).getAsJsonObject();
        		
        		//TODO: What about ca@valencia language?
        		//If YouTube adds this languagecode, remove in line below: && !languageCode.contains("valencia")
        		if(languageInfo.get("translated_words").getAsString() != "0" && !languageCode.contains("valencia")){
        			languageCodes.add(languageCode);
        		}
        	}
        }
        
        return languageCodes;        
    }

    //Gets the content of a caption
    public String getCaption(String resourceSlug, String languageCode) {
		String s = projectSlug + "/resource/" + resourceSlug +"/translation/" + languageCode + "/";
		JsonObject caption = new JsonParser().parse(getResponse(s)).getAsJsonObject();
		return caption.get("content").getAsString();	
    }
    
    public List<String> getAllLanguages(String resourceSlug) {
    	List<String> allLanguages = new ArrayList<String>();
    	
    	String inputText = getResponse(projectSlug+"/resource/"+resourceSlug+"/?details");  
        JsonObject object = new JsonParser().parse(inputText).getAsJsonObject();
        //get the languages for the resource
        JsonArray languageArray = object.get("available_languages").getAsJsonArray();

        for(int j = 0; j < languageArray.size(); j++){
        	JsonObject language = languageArray.get(j).getAsJsonObject();
        	allLanguages.add(language.get("code").getAsString());  	
        }
        
        return allLanguages;
    }
    
    public String getTranslation(String resourceSlug, String languageCode, String text){
    	String inputText = getResponse(projectSlug+"/resource/"+resourceSlug+"/translation/" + languageCode + "/strings/");
    	JsonArray translationStrings = new JsonParser().parse(inputText).getAsJsonArray();
    	for(int k = 0; k < translationStrings.size(); k++){
    		JsonObject translationStr = translationStrings.get(k).getAsJsonObject();
    		if(translationStr.get("source_string").getAsString().contains(text) && !translationStr.get("translation").getAsString().isEmpty()){
    			return translationStr.get("translation").getAsString();
    		}
    	}
    	return null;
    }
        
}