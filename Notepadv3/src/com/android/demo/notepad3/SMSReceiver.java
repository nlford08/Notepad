package com.android.demo.notepad3;

import java.util.Date;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsMessage;
import android.text.format.Time;
import android.util.Log;

/*
 * Some notes:
 * 
 * A BroadcastReceiver object is only valid for the duration of the 
 * call to onReceive(Context, Intent)
 * 
 */



public class SMSReceiver extends BroadcastReceiver {
    protected static final String LOG_TAG = "G2L SMSReceiver";
    protected static int G2L_MESSAGE_AGE_LIMIT = 5 * 60 * 1000; // = 5 minutes
    private Notepadv3 noteapp;
    
    public SMSReceiver(Notepadv3 noteapp) {
    	super();
    	this.noteapp = noteapp;
    }
    
    // This method is called when the BroadcastReceiver is receiving an Intent broadcast.
    @Override
    public void onReceive(Context currentContext, Intent toReceiveSMS)
    {		
    		
            // Unwrap SMS Bundle from "Intent" parameter
            Bundle smsBundle = toReceiveSMS.getExtras();
            
            // Extract array of SmsMessages from Bundle
            // XXX: offer this simplification back to ODK Tables
            SmsMessage[] messages = getSMSs(smsBundle);

            // G2L Interpreter Connect system messages will never exceed 160 characters
            if (messages == null || messages.length > 1) return;    // message is not for us
            SmsMessage message = messages[0];
            
            // empty messages shouldn't be an issue, but just in case...
            String text = message.getDisplayMessageBody();
            if(text == null || text.length() == 0) return;
            
            // get sender's phone number or email
            String sender = message.getDisplayOriginatingAddress(); 
            if (PhoneNumberUtils.isGlobalPhoneNumber(sender)) {
                    sender = PhoneNumberUtils.extractNetworkPortion(sender);
            }
            
            if(true){ // TODO obviously need to change this...

                    // The message IS for us; don't put it into the user's inbox
                    // XXX: offer this feature back to ODK Tables (along with manifest priority change)
                    
                    // Nathan:
                    // SMSs are sent in an ordered broadcast.
                    // If the call receiver is registered to have the highest priority and gets the message first,
                    // it will prevent the message from reaching the users inbox AND the request receiver.
                    // This is good since we don't want to take more requests while waiting for a call
                    
                    this.abortBroadcast();
                    Log.i(LOG_TAG, "SMS from " + sender + " intercepted: \"" + text + "\"");
                    
                    /*
                    // Nathan: I think we can get rid of the toasts since everything is logged.
                      
                    // Display an unobtrusive notice
                    // XXX: offer this simplification back to ODK Tables
                    String noticeString = "SMS from " + sender + ": \n" + text;
                    Toast.makeText(currentContext, noticeString, Toast.LENGTH_LONG).show();
                    */
    
                    String[] words = text.split(" ");
    
                    if(words[0].length() != 1)
                    {
                            Log.e(LOG_TAG, "Discarding message: does not start with one-char action String");
                            return; // message didn't start with one-char action: not for us
                    }
                    
                    // This is so interpreters don't turn off their phone then get a bunch of expired interpreting requests
                    if(new Date().getTime() - message.getTimestampMillis() > G2L_MESSAGE_AGE_LIMIT ){
                            Log.e(LOG_TAG, "Discarding message: too old");
                            return;
                    }

                    actOnMessage(currentContext, sender, words);
            }
    }
    
    // This is how NoteEdit manipulates the database, by having a mDbHelper field.
    private NotesDbAdapter mDbHelper;
    
    /**
     * Performs the action requested by the incoming SMS message:  either notifies the interpreter
     * of a new request; dismisses a previously received request; or causes the phone to dial the
     * interpretee's phone number if the interpreter was the first to accept the request.
     * 
     * @param currentContext        the Context in which the SMSReceiver is running
     * @param sender                                the sender (system server)'s phone number [or email address]
     * @param words                         SMS text (action, request ID, language or interpretee phone number)
     */
    protected void actOnMessage(Context c, String phoneNumber, String[] words){
            
    	
    	// We need to save the message to our notes database.
    	mDbHelper = new NotesDbAdapter(c);
    	
    	// For now, just use the first word as the title
        String title = words[0];
        
        // For now, just use the other words as the body
        String body = "";
        for (int i = 1; i < words.length; i++) {
        	body += words + " ";
        }

        long id = mDbHelper.createNote(title, body);
    	
    	/*  Here is the original body of this function.  Since for now, we just want
    	 * to grab all text messages, we make a note no matter what here.
            if(words.length < 2) return;
            
            char messageCode = words[0].charAt(0);
            int requestID;
            try
            {
                    requestID = Integer.parseInt(words[1]);
            }
            catch(NumberFormatException e)
            {
                    Log.e(LOG_TAG, "Discarding message: requestID is not parseable as an int");
                    return;
            }
            
            switch (messageCode)
            {
            case '+':       // new interpreting request
                    if (isCorrectArrayLength(words, 3)) {
                            recieveRequest(c, phoneNumber, requestID, words[2]);
                    }
                    break;
            case '-':       // dismiss request
                    if (isCorrectArrayLength(words, 2)) {
                            dismissRequest(c, phoneNumber, requestID);
                    }
                    break;
            case '*':       // call command
                    if (isCorrectArrayLength(words, 3)) {
                            callCommand(c, phoneNumber, requestID, words[2]);
                    }
                    break;
            default:                // no match; do nothing
                    Log.e(LOG_TAG, "Discarding message: \""
                                    + messageCode + "\" is not a recognized action");
            }*/
    }
    protected void recieveRequest(Context c, String phoneNumber, int requestID, String language){
            Log.i(LOG_TAG, "Recieved interpretation request.");
    }
    /**
     * Make sure to call the super method if you override this one.
     * @param c
     * @param phoneNumber
     * @param requestID
     */
    protected void dismissRequest(Context c, String phoneNumber, int requestID){
            // Cancel existing status bar notification for this interpreting request
            // What happens if one doesn't exist?
            
            NotificationManager notifier;
            notifier = (NotificationManager) 
                            c.getSystemService(Context.NOTIFICATION_SERVICE);
            notifier.cancel(requestID);
            Log.i(LOG_TAG, "Dismissed notification with id " + requestID);
    }
    protected void callCommand(Context c, String phoneNumber, int requestID, String phoneNumberToCall){
            Log.i(LOG_TAG, "Recieved call command.");
    }
    /**
     * Verify that message appears to be from G2L Interpreter Connect system
     * 
     * @param sender The telephone number of the message sender.
     */
    private boolean senderRecognized(String sender) {
            // Is it possible to spoof phone numbers? Yes I think so...
            // TODO: add digital signature, verify authenticity of server... (Not sure if this is possible over SMS)
            if (sender == null)
            {
                    Log.w(LOG_TAG, "SMS sender was unexpectedly null");
                    return false;
            }
            
            for (String allowedSender : Constants.allowedSenders) {
                    if(PhoneNumberUtils.compare(sender, allowedSender)) {
                            return true;
                    }
            }
            return false;
    }
    /**
     * Extracts an array of SmsMessage objects from a Bundle of incoming PDU-encoded SMS messages.
     *  
     * @param smsBundle     a Bundle containing an incoming message (may be larger than one SMS)
     * @return an array of SmsMessage (or null if the Bundle was null or contained no SMSs)
     */
    private SmsMessage[] getSMSs(Bundle smsBundle)
    {
            if (smsBundle != null)
            {
                    // SMS messages are received as an array of "PDU Mode" encoded byte arrays
                    Object[] pdus = (Object[]) smsBundle.get("pdus");
                    if (pdus != null)
                    {
                            SmsMessage[] messages = new SmsMessage[pdus.length];
                            for (int i = 0; i < messages.length; i++)
                            {
                                    messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                            }
                            return messages;
                    }
            }
            return null;
    }


    /**
     * Determines whether or not the provided array is the expected length.
     */
    protected boolean isCorrectArrayLength(String[] array, int expectedLength)
    {
            boolean correctLength = (array.length == expectedLength);
            if (!correctLength)
                    Log.e(LOG_TAG, "Discarding message: has wrong number of words");
            return correctLength;
    }
}
