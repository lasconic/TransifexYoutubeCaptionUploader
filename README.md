# Transifex YouTube Caption Uploader
---
## The Transifex YouTube Caption Uploader
---
This application automatically uploads translated captions from Transifex to YouTube. It is built to run on Google App Engine (GAE) as a Java servlet and acts on both a GET and POST request.

Upon receiving a GET request, the application check for each registered Transifex resource if it has received a new or updated translation for any defined language since the last check. If so, the resource translation will be uploaded to YouTube, picking the video id from a resource slug - youtube video id map stored in the Google Datastore. This application be can deployed as a cron on Google App Engine.

Upon receiving a POST request, the application will expect the Transifex project and resource slug, and the language code. If the application is triggered by the Transifex webhook, it uploads the caption to the YouTube video for the language. When uploaded, the application sends a notification e-mail to someone. The e-mail contains data which mention the details of the caption: resource slug, language, title and translated title. The title and its translation is optional. In the datastore, a sentence which occurs in the original caption language can be mapped with the resource slug. The application will search for the translation of that sentence (in this case a title of the video) and mention it in the e-mail.

## Tech
---
The web application uses some projects to work properly:

* [Eclipse] - Java environment, easy to use pug-ins
* [YouTube Data API] - Uploading captions on YouTube
* [Transifex API] - Getting captions information
* [Google App Engine] - To run the application on

## Installing the web application
---
Before using the application, some settings have to be changed.

### Google App Engine
First a new project has to be created. Go to https://console.developers.google.com and make one. Click on your project and navigate to: APIs & auth>APIs on the left menu. Under 'Google Cloud APIs', click on 'Cloud Datastore API' and enable the API by clicking on the button. Do the same for 'YouTube Data API' in the YouTube APIs section.

Navigate to: APIs & auth>Credentials and create a new Client ID. Select 'Web application' as Application type and put the following sentences into the 'Authorized redirect URIs' box:
```
http://YOUR_PROJECT_ID.appspot.com/oauth2callback
http://localhost:8888/oauth2callback
```
Note: Change YOUR_PROJECT_ID with the ID of your project you just created. If you don't remember is, visit the developer's console again (see beginning of this topic).

When the Client ID is created, it appears in the OAuth section. Download the JSON-file of the Client ID by clicking on the JSON button below the Client ID data. Rename the file to client_secrets.json . Place the file into the war directory of the application (you see another client_secrets.json file, replace it with your file). 

### Eclipse
The Google plugin need to be installed to deploy the app on the GAE. To do so, navigate in Eclipse to Help>Install new Software. In the upper field, paste the following URL:
```
https://dl.google.com/eclipse/plugin/
```
Now Eclipse will search for Google Software. When loaded, select the 'Google Plugin for Eclipse' checkbox and click 'Next' to install the Software. After installation, login with your Google account in the right bottom of the Eclipse window.

Import the webapplication in Eclipse
```
https://github.com/maalliet/TransifexYoutubeCaptionUploader
```

As speaking now, the GAE only supports Java JDK version 1.7. When you installed newer version of Java, right click the imported project and go to the properties. Navigate in the menu to 'Java Compiler' and change the 'Compiler compliance level' to 1.7. You'll get an error each time you try to deploy due to this setting. You can ignore that error. Click 'OK' to save the settings.

To deploy the application on the GAE, you need to set the application ID. To do this, right click the project, go to the properties. Expand the 'Google' topic in the menu and select 'App Engine'. In the 'Deployment' section you'll see now a box where you can change the application ID. Insert your application ID, which is the same as your project ID you created before in the Google Developers Console (see topic: Google App Engine). Click 'OK' to save the settings.

### Transifex
To get authorized access to the Transifex API, you need to change the auth variable in the CaptionsServlet.java file.  
```Java
private String auth = ""; //in form: username:password
```
Let's pretend your Transifex username is 'MyUsername' and password 'MyPassword'. The variable need to be set as follows:
```Java
private String auth = "MyUsername:MyPassword"; //in form: username:password
```
The second variable that need to be changed is the project slug.

>slug: A small string that will be used in the URL of a project or resource. The allowed characters are the alphanumeric characters as well as the characters '_' and '-'. [Transifex]

After logging in on Transifex, you will be redirected to the dashboard. Select your Transifex project on the left menu. In the URL, your project slug will appear at the end. For example: the URL you get is
```
https://www.transifex.com/organization/myOrganisation/dashboard/project-sample
```
 so your project slug is 'project-sample'. Set the projectSlug variable in the CaptionsServlet.java file as follows:
```Java
private String projectSlug = "project-sample";
```
### E-mail
In the introduction, it's mentioned that the application sends an email to a person when the Transifex Webhook is activated. Some settings need to be changed in order to send e-mails. Open the CaptionsServlet.java file and go to the part in the doPost() method where the e-mail is sent.
```Java
//Sending mail
Properties props = new Properties();
Session session = Session.getDefaultInstance(props, null);
Message msg = new MimeMessage(session);
msg.setFrom(new InternetAddress("YOUR_GMAIL", "NAME_SENDER"));
msg.addRecipient(Message.RecipientType.TO,
	  new InternetAddress("EMAIL_RECEIVER", "NAME_RECEIVER"));
msg.setSubject("A new translation is completed");
msg.setText(msgBody);
Transport.send(msg);
```
Change the following variables in code above:
- YOUR_GMAIL : your GMAIL address
- NAME_SENDER : the name of the sender (your name for example)
- EMAIL_RECEIVER : the e-mail address of the receiver
- NAME_RECEIVER : the name of the person/organisation who receives the e-mail
- A new translation is completed : You can change this subject of the e-mail if you want to (optional)

## Adding data to the Google Datastore
---
Like said before, the application uses the Google Datastore to set the mapping between Transifex slugs and YouTube video's. This ensures that you don't need to change the script when you want to add a new mapping. A second mapping in the datastore is to get the translation of a specific text (for example a title) in the caption when the Transifex Webhook is activated.

To have access to you datastore, visit the Developers Console on https://console.developers.google.com and select your project. Navigate in the menu to: Storage>Cloud Datastore>Query.

First of all it's important to know how to get the Transifex resource slugs. Login on Transifex and navigate to your resources. When clicking on one of them, you'll be navigated to a new page where all the possible languages are displayed with the percentage of translation completion. The URL of that page is similar to:
```
https://www.transifex.com/projects/p/project-sample/resource/resourceslug/
```
Only 'project-sample' and 'resorceslug' will be different. What appears in the last part of the URL is the resource slug (in this case 'resourceSlug'). It's the caption identifier.

### Slug - Video mapping
To add a Slug - Video mapping, click the 'Create an entity' button on the upper side of the screen. For the first entity you need to select 'New Kind' in the dropdownbox next to the 'Kind' label and write 'SlugVideoMapping' in the textbox that appears. When adding more entities, you can select 'SlugVideoMapping' in the dropdownbox next to the 'Kind' label. 
Select 'Custom Name' in the dropdownbox below the 'Key Identifier Type' and paste the Transifex resource slug in the textbox that appears with label 'Key Name'. Create a new property with name 'VideoID' (leave the dropdown boxes on values 'A string' and 'Indexed'). Set the property in the second text field to the video ID of your YouTube video. This is the small part that comes after 
```
https://www.youtube.com/watch?v=
```
in the URL when looking to your video. For example; when the URL of you video is
```
https://www.youtube.com/watch?v=YZ4kRzkHroU
```
the ID of the video equals to: YZ4kRzkHroU
Click 'Create entity' and repeat these steps if you have more resources that need to be linked to a YouTube video.

### Text Translation mapping
When the Transifex Webhook is activated, an email will be sent to a person that mentions the completion of a translation. If you want to include a specific translation of a string in the e-mail (for example a title translation that you could use in .po-files), you have to add a Text Translation mapping.

To add a Text Translation mapping, click the 'Create an entity' button on the upper side of the screen. For the first entity you need to select 'New Kind' in the dropdownbox next to the 'Kind' label and write 'TextTranslation' in the textbox that appears. When adding more entities, you can select 'TextTranslation' in the dropdownbox next to the 'Kind' label. Don't chance the 'Key Identifier Type' this time, it must contain the 'ID (auto-generated) value. Create a new property with name 'Slug' (leave the dropdown boxes on values 'A string' and 'Indexed'). Set the property in the second text field to the Transifex resource slug. Add a new item by clicking on the correspond button (only first time, then it will be done automatically). Set the name of the property to 'Text'. Choose the same values (A string, Indexed) for the dropdownboxes and set the value of the property to the string which you want to get a translation from. Click 'Create entity' and repeat these steps if you have more resources that need to be linked to a text to get a translation from in the e-mail.
## Running the app for the first time
---
When everything is changed correctly, you can deploy the application on the GAE in Eclipse. Right click on the project in Eclipse and navigate to: Google>Deploy to App Engine. When deployed, a new window appears in your browser. Click on the 'OAuth2' link and give permission to get access to your account. When done so, you can click on the 'Captions' link, when visiting your application on the GAE. If you ever receive a 'no credits' message when clicking on the 'Captions' link, you need to run the 'OAuth2' part again.
