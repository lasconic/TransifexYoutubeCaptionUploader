package com.musescore.captions;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.logging.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTube.Captions.Insert;
import com.google.api.services.youtube.YouTube.Captions.Update;
import com.google.api.services.youtube.model.Caption;
import com.google.api.services.youtube.model.CaptionListResponse;
import com.google.api.services.youtube.model.CaptionSnippet;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;

@SuppressWarnings("serial")
public class CaptionsServlet extends HttpServlet {

	// Define a global instance of the HTTP transport.
	public static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static final Logger log = Logger.getLogger(CaptionsServlet.class.getName());

	private static YouTube youtube;
	
	private Map<String, String> urlMap = new HashMap<String, String>();
	
	//Transifex Webhook
	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {

			if(req.getParameter("project").equals(Config.TRANSIFEX_PROJECT_SLUG)){
				String resource = req.getParameter("resource");
				String code = req.getParameter("language");
				
				// Load client secrets.
				Reader clientSecretReader = new InputStreamReader(getServletContext().getResourceAsStream("/client_secrets.json"));
				GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(), clientSecretReader);
				Credential credential = AuthUtils.getCredential(AuthUtils.USER_ID, clientSecrets);
	
				if (credential == null) {
					resp.setContentType("text/plain");
					resp.getWriter().println("no credentials...");
					return;
				}
	
				// This object is used to make YouTube Data API requests.
				youtube = new YouTube.Builder(HTTP_TRANSPORT,
						JacksonFactory.getDefaultInstance(), credential)
						.setApplicationName("youtube-cmdline-captions-sample")
						.build();
				
				//Fill Map with slugs and videoID
				DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();	
				Query gaeQuery = new Query("SlugVideoMapping");
				PreparedQuery pq = datastore.prepare(gaeQuery);
				List<Entity> list = pq.asList(FetchOptions.Builder.withDefaults());
				for(Entity e : list){
					String id = (String)e.getProperty("VideoID");
					String slug = e.getKey().getName();
					urlMap.put(slug, id);
				}
				
				Transifex transifex = new Transifex(Config.TRANSIFEX_PROJECT_SLUG, Config.TRANSIFEX_AUTH);		
				//Putting caption on youtube
				if(urlMap.containsKey(resource)){
					String caption = transifex.getCaption(resource, code);
					
					//Check if language already exists; if so: update, if not: upload
					String videoID = urlMap.get(resource);
					String captionID = this.getCaptionID(videoID, code);
					if(captionID == null){
						//upload method
						uploadCaption(videoID, code, "", caption);
					} else {
						//update method
						updateCaption(captionID, caption);
			    	}
				} else {
					return;
				}
			
				//Sending mail with all translations
				//
				//Getting translations
				gaeQuery = new Query("TextTranslation");
				pq = datastore.prepare(gaeQuery);
				list = pq.asList(FetchOptions.Builder.withDefaults());
				//Body message of mail
				String msgBody = "Resource: " + resource + "\n";
				for(Entity e : list){
					if(e.getProperty("Slug").equals(resource)){
						msgBody += "Title: " + (String)e.getProperty("Text") + "\n" + "Language: " + code + "\n";
						String translation = transifex.getTranslation(resource, code, (String)e.getProperty("Text"));
						msgBody += "Translated Title: " + translation;
					}
				}
				//
				//Sending mail
				Properties props = new Properties();
				Session session = Session.getDefaultInstance(props, null);
				Message msg = new MimeMessage(session);
				msg.setFrom(new InternetAddress("musescore.donation@gmail.com", "Transifex to Youtube"));
				msg.addRecipient(Message.RecipientType.TO,
					  new InternetAddress("thomas@musescore.com", "Thomas"));
				msg.setSubject("A new translation is completed");
				msg.setText(msgBody);
				Transport.send(msg);
				
			}
		} catch (MessagingException e){
			e.printStackTrace();
		}
	}
	
	//Update all captions
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setContentType("text/plain");
		
		try {
			// Load client secrets.
			Reader clientSecretReader = new InputStreamReader(getServletContext().getResourceAsStream("/client_secrets.json"));
			GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(), clientSecretReader);
			Credential credential = AuthUtils.getCredential(AuthUtils.USER_ID, clientSecrets);

			if (credential == null) {
				resp.setContentType("text/plain");
				resp.getWriter().println("no credentials...");
				return;
			}

			// This object is used to make YouTube Data API requests.
			youtube = new YouTube.Builder(HTTP_TRANSPORT,
					JacksonFactory.getDefaultInstance(), credential)
					.setApplicationName("youtube-cmdline-captions-sample")
					.build();
			
			//Fill Map with slugs and videoID
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();	
			Query gaeQuery = new Query("SlugVideoMapping");
			PreparedQuery pq = datastore.prepare(gaeQuery);
			List<Entity> list = pq.asList(FetchOptions.Builder.withDefaults());
			resp.getWriter().println("== Found these Slugs with VideoID ==");
			for(Entity e : list){
				String id = (String)e.getProperty("VideoID");
				String slug = e.getKey().getName();
				resp.getWriter().println(slug + " - " + id);
				urlMap.put(slug, id);
			}
			resp.getWriter().println("====================================");
			resp.getWriter().println();

			//Save Date
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	    	dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	    	String thisDate = dateFormat.format(new Date());
			resp.getWriter().println("Date now (Transifex Time): " + thisDate.toString());
			
			Transifex transifex = new Transifex(Config.TRANSIFEX_PROJECT_SLUG, Config.TRANSIFEX_AUTH);
			List<String> resourceSlugs = transifex.getAllResourceSlugs();
			for(String slug : resourceSlugs){
				if(urlMap.containsKey(slug)){
					resp.getWriter().println("===== Checking resource: " + slug + " =====");
					log.warning("===== Checking resource: " + slug + " =====");
					//Get last runtime date for the resource
					Date lastRuntime = getLastDate(slug);
					//Get all the captions that need an update
					List<String> languages = transifex.getDepricatedLanguages(slug, lastRuntime);
					for(String code : languages){
						//Check if language is uploaded yet
						Date lastDateLanguage = getLastDate(slug, code);
						resp.getWriter().println("\t - language: " + code + " - lastDatelang :" + lastDateLanguage + " - lastDate resource " + lastRuntime);
						if(lastRuntime == null  || lastDateLanguage == null || (lastRuntime != null && lastDateLanguage != null && lastDateLanguage.after(lastRuntime))) {

							//Get the caption content
							String caption = transifex.getCaption(slug, code);
							
							//Check if language already exists; if so: update, if not: upload
							String videoID = urlMap.get(slug);
							String captionID = this.getCaptionID(videoID, code);
							if(captionID == null){
								//upload method
								resp.getWriter().println("\t - Uploading language: " + code + " on videoID: " + urlMap.get(slug));
								log.warning("Uploading language: " + code + " on videoID: " + urlMap.get(slug));
								this.uploadCaption(videoID, code, "", caption);
							} else {
								//update method
								resp.getWriter().println("\t - Updating language: " + code + " on videoID: " + urlMap.get(slug));
								log.warning("Updating language: " + code + " on videoID: " + urlMap.get(slug));
								this.updateCaption(captionID, caption);
							}
							//Save language update time into the datastore
							Entity e = new Entity(slug, code);
							e.setProperty("date", thisDate);
							datastore.put(e);
				    	}	
					}
					//Save Resource update time into the datestore
					Entity e = new Entity("lastRuntime", slug);
					e.setProperty("date", thisDate);
					datastore.put(e);			
					resp.getWriter().println("-> Updated last runtime in datastore");
				}
			}
	
			resp.getWriter().println("Upload DONE !");			
			return;
		} catch (ParseException e) {
			resp.getWriter().println("ParseException");
			resp.getWriter().println(e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			resp.getWriter().println("IOException");
			resp.getWriter().println(e.getMessage());
			e.printStackTrace();
		}

		resp.getWriter().println("An error occured");
	}
	
	//Get last update time from a specific resource
	private Date getLastDate(String resourceSlug){
		try {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			Key key = KeyFactory.createKey("lastRuntime", resourceSlug);
			Entity e = datastore.get(key);
			String date = (String)e.getProperty("date");
			DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
            return df.parse(date);
		} catch (EntityNotFoundException e) {
			return null;
		} catch (ParseException e){
			return null;
		}
	}

	//Get last update time from a specific language of a resource
	private Date getLastDate(String resourceSlug, String languageCode){
		try {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			Key key = KeyFactory.createKey(resourceSlug, languageCode);
			Entity e = datastore.get(key);
			String date = (String)e.getProperty("date");
			DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
            return df.parse(date);
		} catch (EntityNotFoundException e) {
			return null;
		} catch (ParseException e){
			return null;
		}
	}
	
	//gets the captionID
	private String getCaptionID(String videoID, String captionLanguage) throws IOException {
    	CaptionListResponse captionListResponse = youtube.captions().
	          list("snippet", videoID).execute();
	
    	List<Caption> captions = captionListResponse.getItems();
    	//changes _ into -
    	String lng;
    	if(captionLanguage.contains("_")){
    		lng = captionLanguage.replace("_", "-");
    	} else {
    		lng = captionLanguage;
    	}
    	
    	//Return the caption ID
	    CaptionSnippet snippet;
	    for (Caption caption : captions) {
	        snippet = caption.getSnippet();
	        if(snippet.getLanguage().equals(lng)){
	        	return caption.getId();
	        }
	    }
	    //return null when nothing found
    	return null;
    }
	
	public void uploadCaption(String videoId, String captionLanguage, String captionName, String captionContent) throws IOException {
	      // Add extra information to the caption before uploading.
	      Caption captionObjectDefiningMetadata = new Caption();

	      // Most of the caption's metadata is set on the CaptionSnippet object.
	      CaptionSnippet snippet = new CaptionSnippet();

	      // Set the video, language, name and draft status of the caption.
	      snippet.setVideoId(videoId);
	      snippet.setLanguage(captionLanguage);
	      snippet.setName(captionName);
	      snippet.setIsDraft(false);

	      // Add the completed snippet object to the caption resource.
	      captionObjectDefiningMetadata.setSnippet(snippet);
	      // Create an object that contains the caption file's contents.
	      byte[] bytes = captionContent.getBytes("UTF-8");
	      InputStreamContent mediaContent = new InputStreamContent( "*/*", new BufferedInputStream(new ByteArrayInputStream(bytes)));
	      mediaContent.setLength(bytes.length);
	      
	      // Create an API request that specifies that the mediaContent
	      // object is the caption of the specified video.
	      Insert captionInsert = youtube.captions().insert("snippet", captionObjectDefiningMetadata, mediaContent);

	      // Set the upload type and add an event listener.
	      MediaHttpUploader uploader = captionInsert.getMediaHttpUploader();

	      // Indicate whether direct media upload is enabled. A value of
	      // "True" indicates that direct media upload is enabled and that
	      // the entire media content will be uploaded in a single request.
	      // A value of "False," which is the default, indicates that the
	      // request will use the resumable media upload protocol, which
	      // supports the ability to resume an upload operation after a
	      // network interruption or other transmission failure, saving
	      // time and bandwidth in the event of network failures.
	      uploader.setDirectUploadEnabled(false);
	      
	      captionInsert.execute();
	    }

	public void updateCaption(String captionId, String captionContent) throws IOException {
		// Modify caption's isDraft property to unpublish a caption track.
	    CaptionSnippet updateCaptionSnippet = new CaptionSnippet();
	    updateCaptionSnippet.setIsDraft(false);
	    Caption updateCaption = new Caption();
	    updateCaption.setId(captionId);
	    updateCaption.setSnippet(updateCaptionSnippet);  

	    // Create an object that contains the caption file's contents.
	    byte[] bytes = captionContent.getBytes("UTF-8");
	    InputStreamContent mediaContent = new InputStreamContent( "*/*", new BufferedInputStream(new ByteArrayInputStream(bytes)));
	    mediaContent.setLength(bytes.length);

        // Create an API request that specifies that the mediaContent
        // object is the caption of the specified video.
        Update captionUpdate = youtube.captions().update("snippet", updateCaption, mediaContent);

        // Set the upload type and add an event listener.
        MediaHttpUploader uploader = captionUpdate.getMediaHttpUploader();

        // Indicate whether direct media upload is enabled. A value of
        // "True" indicates that direct media upload is enabled and that
        // the entire media content will be uploaded in a single request.
        // A value of "False," which is the default, indicates that the
        // request will use the resumable media upload protocol, which
        // supports the ability to resume an upload operation after a
        // network interruption or other transmission failure, saving
        // time and bandwidth in the event of network failures.
        uploader.setDirectUploadEnabled(false);

        // Upload the caption track.
        captionUpdate.execute();
	}
}

