package com.musescore.captions;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.extensions.appengine.datastore.AppEngineDataStoreFactory;
import com.google.api.client.extensions.appengine.http.UrlFetchTransport;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStore;

public final class AuthUtils
{
  public static final String OAUTH2_PATH = "/oauth2callback";
  
  public static final String USER_ID = "captions";
  
  public static final List<String> SCOPES = Arrays.asList(
		    "https://www.googleapis.com/auth/youtube.force-ssl"
		  );

  public static Credential getCredential(String userId,  GoogleClientSecrets clientSecrets) throws IOException {
    return userId == null ? null : buildCodeFlow(clientSecrets).loadCredential( userId );
  }

  public static void deleteCredential(String userId) throws IOException {
    getDataStore().delete( userId );
  }

  public static DataStore<StoredCredential> getDataStore()
    throws IOException
  {
    AppEngineDataStoreFactory factory = AppEngineDataStoreFactory.getDefaultInstance();
    return StoredCredential.getDefaultDataStore( factory );
  }

  /**
   * Gets the credentials stored in GAE. If expired, uses the refresh token to
   * get a new access token from OAuth
   * @return
   * @throws IOException
   */
// START:flow
  public static AuthorizationCodeFlow buildCodeFlow(GoogleClientSecrets clientSecrets)
      throws IOException
  {
    return new GoogleAuthorizationCodeFlow.Builder(
        new UrlFetchTransport(),
        new JacksonFactory(),
        clientSecrets,
        SCOPES)
    .setApprovalPrompt( "force" )
    .setAccessType("offline")
    .setCredentialDataStore( getDataStore() )
    .build();
  }
// END:flow
  
  public static String fullUrl( HttpServletRequest req, String rawPath )
  {
    GenericUrl url = new GenericUrl( new String(req.getRequestURL()) );
    url.setRawPath( rawPath );
    return url.build();
  }
}