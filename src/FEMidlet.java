/*
 * FEMidlet.java
 *
 */

package net.yahoo.pinpoint;

import java.io.IOException;
import java.util.Hashtable;
import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import net.oauth.j2me.token.AccessToken;
import net.oauth.j2me.token.RequestToken;
import javax.microedition.location.*;
import net.oauth.j2me.OAuthServiceProviderException;
import net.oauth.j2me.BadTokenStateException;
import net.yahoo.fireeagle.j2me.FireEagleConsumer;

public class FEMidlet extends MIDlet implements CommandListener, Runnable {
    
    static final String RECORD_STORE_NAME="FEoauthTokens";   
    static final String OAUTH_CONSUMER_TOKEN="VIGmpgmn4ED8";
    static final String OAUTH_CONSUMER_SECRET="Fas4xsTmNaPTN3sTMiWup5pzA79yK4nm";
    
    /** Creates a new instance of the MIDlet */
    public FEMidlet() {
    }
    
    private Form helloForm;
    private Form authForm;
    private Form gpsForm;
    private Form enterLocForm;
    
    private Command exitCommand;
    private Command showAuthCommand;
    private Command authStartCommand;
    private Command authNextCommand;
    private Command showManualUpdateCommand;
    private Command submitUpdateCommand;
    private Command homeCommand;
    private Command startGPSCommand;
    private Command stopGPSCommand;
    private Command showGPSCommand;
    
    private StringItem helloStringItem;
    private StringItem stringItemCode;
    private StringItem stringItemInstuctions;
    private StringItem authURL;
    private StringItem yourLoc;
    private StringItem coordStringItem;
    private StringItem feLocStringItem;
    private Spacer spacer1;
    private TextField locEntryField;
    
    private static final int _ASYNC_NOTHING = 0;
    private static final int _ASYNC_CA = 1;
    private int _asyncMethod = _ASYNC_NOTHING;
    private Object[] _asyncParameters = new Object[2];
    
    private FireEagleConsumer fireEagleClient;
    private RecordStore rs;
    private LocationRetriever ret;
    
    /***************
     * These methods at the top do all the interesting OAuth stuff and the interesting bits of midlet logic
     *******/
    
    public void startOauth() {
        RequestToken requestToken=null;
        try {
            requestToken=fireEagleClient.fetchNewRequestToken();    
        } catch (OAuthServiceProviderException e) {
            System.out.println("error getting request token "+e.getHTTPResponseCode()+" : "+e.getHTTPResponse());
            helloStringItem.setText("oauth trouble: "+fireEagleClient.naiveParseErrorResponse(e.getHTTPResponse()));
        }
        if (requestToken!=null) {
            System.out.println("Got request token: "+requestToken.getToken());
            stringItemCode.setLabel(requestToken.getToken());
            authForm.addCommand(this.get_authNextCommand());
        } else {
            System.out.println("error getting request token");
            // TODO -- give user a way to try again...
        }
        
    }
    
    // throw no request token exception if request token not set
    public void exchangeToken() {
        AccessToken accessToken=null;
        try {
            accessToken=fireEagleClient.fetchNewAccessToken();
        } catch (BadTokenStateException e) {
            System.out.println("error getting access token "+e.toString());
        } catch (OAuthServiceProviderException e) {
            System.out.println("error getting access token "+e.getHTTPResponseCode()+" : "+e.getHTTPResponse());
            helloStringItem.setText("oauth trouble: "+fireEagleClient.naiveParseErrorResponse(e.getHTTPResponse()));
        }
        // TODO -- more error handling
        
        getDisplay().setCurrent(get_helloForm());

        if (accessToken!=null) {
            System.out.println("Got access token: "+accessToken.getToken());
            this.storeAccessToken(accessToken);
            helloForm.addCommand(get_showManualUpdateCommand());
            helloForm.addCommand(this.get_showGPSCommand());
            helloStringItem.setText("You authorized!");
        } else {
            helloStringItem.setText("There was a problem.  You can try to authorize again...");
        }
        
    }
    
    public void submitManualUpdate() {
        Hashtable queryParams=new Hashtable(1);
        String loc=this.get_locEntryField().getString();
        System.out.println("location from text box="+loc);
        queryParams.put("q",loc);
        //String parsedLoc=
        updateAndQuery(queryParams);
        //get_yourLoc().setText(parsedLoc);
        //setDisplayLoc(parsedLoc);
    }
    
    public void submitGPSUpdate(String lat, String lon) {
        Hashtable queryParams=new Hashtable(2);
        queryParams.put("lat",lat);
        queryParams.put("lon", lon);
        //String parsedLoc=
        updateAndQuery(queryParams);
        //this.get_feLocStringItem().setText(parsedLoc);
        //setDisplayLoc(parsedLoc);
    }
    
    private String updateAndQuery(Hashtable queryParams) {
        String updateError=updateFELoc(queryParams);
        if (updateError != null) {
            return updateError;
        }
        return queryFELoc();
    }
    
    private String queryFELoc() {
        String resp=null;
        String parsedResp=null;
        setDisplayLoc("querying FE...");
        try {
            resp=fireEagleClient.queryUserLocation();
            System.out.println("Got a response:\n"+resp);
        } catch (BadTokenStateException e) {
            System.out.println("error querying "+e.toString());
            parsedResp= "query failed: "+e.toString();
        } catch (OAuthServiceProviderException e) {
            System.out.println("error querying "+e.getHTTPResponseCode()+" : "+e.getHTTPResponse());
            parsedResp= "query failed: "+fireEagleClient.naiveParseErrorResponse(e.getHTTPResponse());
        } catch (IOException e) {
            System.out.println("error querying "+e.toString());
            parsedResp= "query failed: "+e.toString();
        }
        if (parsedResp == null) {
            parsedResp=fireEagleClient.naiveParseQueryResponse(resp);
        }
        
        setDisplayLoc(parsedResp);
        return parsedResp;
    }
    
    private String updateFELoc(Hashtable queryParams) {
        String resp=null;
        try {
            resp=fireEagleClient.updateLocation(queryParams); //accessProtectedResource(OAUTH_HOST+UPDATE_API_URL, accessToken, queryParams, "POST");
            System.out.println("Got a response:\n"+resp);
        } catch (BadTokenStateException e) {
            System.out.println("error updating "+e.toString());
            return "update failed: "+e.toString();
        } catch (OAuthServiceProviderException e) {
            System.out.println("error updating "+e.getHTTPResponseCode()+" : "+e.getHTTPResponse());
            return "update failed: "+fireEagleClient.naiveParseErrorResponse(e.getHTTPResponse());
        } catch (IOException e) {
            System.out.println("error updating "+e.toString());
            return "update failed: "+e.toString();
        }
        return null; // return null if all goes well (we don't really care about the FE response)
    }

    // this is probably badly named -- it's the callback from LocationRetriever
    public void updateLocation(double lat, double lon, String errString) {
        String string;
        if (errString != null){
            this.get_coordStringItem().setText(errString);
        } else {
            this.get_coordStringItem().setText("\nLatitude : " + lat + "\nLongitude : " + lon);
             this.submitGPSUpdate(""+lat, ""+lon);
        }
    }
    
    private void setDisplayLoc(String locString) {
        // there are a couple different StringItems we want to update (could check which form is currently displayed...
        get_yourLoc().setText(locString);
        get_feLocStringItem().setText(locString);
        get_helloStringItem().setLabel("Current FE loc:");
        get_helloStringItem().setText(locString);
    }
    
    private void startGPS() {
        get_coordStringItem().setText("starting GPS");
        //if (ret==null) {
            ret = new LocationRetriever(this);
        //}
            // TODO -- stop the ld one?
        ret.start();
    }
    
    private void stopGPS() {
        get_coordStringItem().setText("GPS stopped");
        ret.stop();
    }
    
    private AccessToken readAccessToken() {
        System.out.println("readAccessToken");
        AccessToken at=null;
        String tok=null;
        String sec=null;
        try {
            RecordEnumeration e=rs.enumerateRecords(null, null , false);
            while (e.hasNextElement()) {
                String s=new String(e.nextRecord());
                if (s.startsWith("S")) {
                    sec=s.substring(1);
                } else if (s.startsWith("T")) {
                    tok=s.substring(1);
                }
            }
            if (sec!=null && tok!=null) {
                at=new AccessToken(tok, sec);
            }
        } catch (RecordStoreException rse) {
            System.err.println(rse.toString());
        }
        return at;
    }
    
    private boolean storeAccessToken(AccessToken at) {
        System.out.println("storeAccessToken");
        boolean success=true;
        // might be easiest just to delete recordstore and start over
        try {
            rs.closeRecordStore();
            RecordStore.deleteRecordStore(RECORD_STORE_NAME);
            rs=RecordStore.openRecordStore(RECORD_STORE_NAME, true);
        } catch (RecordStoreException e) {
            ;
        }
        try {
            String t="T"+at.getToken(); // token starts with T
            String s="S"+at.getSecret(); // secret starts with S
            rs.addRecord(s.getBytes(), 0, s.length());
            rs.addRecord(t.getBytes(), 0, t.length());
        } catch (RecordStoreException rse) {
            System.err.println(rse.toString());
            success=false;
        }
        
        return success;
    }
    
    
    /*****************
     * Now we get into basic midlet maintenance methods -- handling commands, initializing, dying, etc
     *******/
    
    /** This method initializes UI of the application.
     */
    private void initialize() {
        fireEagleClient=new FireEagleConsumer(OAUTH_CONSUMER_TOKEN, OAUTH_CONSUMER_SECRET);
        //oauthConsumer.setSignatureMethod("HMAC-SHA1");
        try {
            System.out.println("creating recordstore");
            rs = RecordStore.openRecordStore(RECORD_STORE_NAME,  true);
        } catch (RecordStoreException rse) {
            System.err.println(rse.toString());
        }
        new java.lang.Thread(this).start();
        getDisplay().setCurrent(get_helloForm());
        
        AccessToken accessToken=this.readAccessToken();
        if (accessToken != null) {
            this.get_helloStringItem().setText("you've got a token");
            fireEagleClient.setAccessToken(accessToken);
            System.out.println("existing tok="+accessToken.getToken());
            this.queryFELoc(); // try to query as we start up
        } else {
            helloForm.removeCommand(get_showManualUpdateCommand());
            helloForm.removeCommand(this.get_showGPSCommand());
        }
    }
    
    // WARNING - "Generate Threaded Command Listeners" document property is deprecated - use WaitScreen components instead.
    // It will NOT be possible to open/import this file in the future.
    
    /** This method is run in another thread and communicates with event handlers and provides ability for event processing in different thread.
     *  -- this was auto-generated by NetBeans and is actually kinda cool...means you don't need to handle threading in network access stuff
     *  	since the UI stuff runs in its own thread already
     **/
    public void run() {
        int asyncMethod;
        Object firstParameter;
        Object secondParameter;
        
        for (;;) {
            // wait and get request for event processing
            synchronized (_asyncParameters) {
                _asyncMethod = _ASYNC_NOTHING;
                _asyncParameters[0] = null;
                _asyncParameters[1] = null;
                try {
                    _asyncParameters.wait();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                asyncMethod = _asyncMethod;
                firstParameter = _asyncParameters[0];
                secondParameter = _asyncParameters[1];
            }
            
            // forward request to appropriate event handler
            if (_asyncMethod == _ASYNC_CA)
                _commandAction((Command) firstParameter, (Displayable) secondParameter);
        }
    }
    
    /** Called by the system to indicate that a command has been invoked on a particular displayable.
     * @param command the Command that ws invoked
     * @param displayable the Displayable on which the command was invoked
     */
    public void commandAction(Command command, Displayable displayable) {
        synchronized (_asyncParameters) {
            _asyncMethod = _ASYNC_CA;
            _asyncParameters[0] = command;
            _asyncParameters[1] = displayable;
            _asyncParameters.notifyAll();
        }
    }
    
    
    /** Called by the background thread to indicate that a command has been invoked on a particular displayable.
     * @param command the Command that ws invoked
     * @param displayable the Displayable on which the command was invoked
     */
    public void _commandAction(Command command, Displayable displayable) {
        // Insert global pre-action code here
        if (displayable == helloForm) {
            if (command == exitCommand) {
                exitMIDlet();
            } else if (command == showManualUpdateCommand) {
                getDisplay().setCurrent(get_enterLocForm());
            } else if (command == showAuthCommand) {
                getDisplay().setCurrent(get_authForm());
                authForm.removeCommand(this.get_authNextCommand());
                startOauth();
            } else if (command == showGPSCommand) {
                getDisplay().setCurrent(get_gpsForm());
                if (ret == null || !ret.isRunning()) {
                    startGPS();
                }
            }
        } else if (displayable == enterLocForm) {
            if (command == submitUpdateCommand) {
                this.submitManualUpdate();
            } else if (command == homeCommand) {
                getDisplay().setCurrent(get_helloForm());
            }
        } else if (displayable == authForm) {
            if (command == authNextCommand) {
                exchangeToken();
            }
        } else if (displayable == gpsForm) {
            if (command == startGPSCommand) {
                get_coordStringItem().setText("starting GPS...");
                startGPS();
            } else if (command==stopGPSCommand) {
                stopGPS();
                get_coordStringItem().setText("GPS stopped");
            }
        }
        
        if (command == homeCommand) {
            getDisplay().setCurrent(get_helloForm());
            
        }
    }
    
    public void startApp() {
        initialize();
    }
    
    public void pauseApp() {
    }
    
    public void destroyApp(boolean unconditional) {
        System.out.println("destroyApp");
        exitMIDlet();
        /*
        
        try {
            rs.closeRecordStore();
            //rs.deleteRecordStore(RECORD_STORE_NAME);
        } catch (RecordStoreException rse) {
            System.err.println(rse.toString());
        }
        rs=null;
        if (ret != null) {
            ret.stop();
            if (ret.isAlive()) {
                ret.interrupt();
                if (ret.isAlive()) {
                    try {
                        ret.join(); // wait for it to die
                    } catch (InterruptedException ignored) {}
                }
            }
            ret=null;
        }
         **/
    }
    
    public void exitMIDlet() {
        System.out.println("exitMidlet");
        try {
            rs.closeRecordStore();
            //rs.deleteRecordStore(RECORD_STORE_NAME);
        } catch (RecordStoreException rse) {
            System.err.println(rse.toString());
        }
        rs=null;
        getDisplay().setCurrent(null);
        if (ret != null) {
            ret.stop();
            if (ret.isAlive()) {
                ret.interrupt();
                if (ret.isAlive()) {
                    try {
                        ret.join(); // wait for it to die
                    } catch (InterruptedException ignored) {}
                }
            }
            ret=null;
        }
        notifyDestroyed();
    }
    
    /*************************************************************************
     * whole bunch of helper methods to get various commands, form items, etc
     *******/
    public Display getDisplay() {
        return Display.getDisplay(this);
    }
    
    public Form get_helloForm() {
        if (helloForm == null) {
            helloForm = new Form(null, new Item[] {get_helloStringItem()});
            helloForm.addCommand(get_exitCommand());
            helloForm.addCommand(get_showAuthCommand());
            helloForm.addCommand(get_showManualUpdateCommand());
            helloForm.addCommand(get_showGPSCommand());
            helloForm.setCommandListener(this);
        }
        return helloForm;
    }
    
    public StringItem get_helloStringItem() {
        if (helloStringItem == null) {
            helloStringItem = new StringItem("Hello", "You need to authorize.  Select Authorize from the menu to get started");
        }
        return helloStringItem;
    }
    
    public Command get_exitCommand() {
        if (exitCommand == null) {
            exitCommand = new Command("Exit", Command.EXIT, 1);
        }
        return exitCommand;
    }
    
    public Form get_authForm() {
        if (authForm == null) {
            authForm = new Form("Authorize", new Item[] {
                get_stringItemCode(),
                get_spacer1(),
                get_stringItemInstuctions(),
                get_authURL()
            });
            authForm.addCommand(get_authNextCommand());
            authForm.setCommandListener(this);
            authForm.addCommand(get_homeCommand());
            get_authURL().setText(fireEagleClient.MOBILE_AUTH_URL);
        }
        return authForm;
    }
    
    
    public Command get_showAuthCommand() {
        if (showAuthCommand == null) {
            showAuthCommand = new Command("Authorize", Command.SCREEN, 1);
        }
        return showAuthCommand;
    }
    
    public Command get_authStartCommand() {
        if (authStartCommand == null) {
            authStartCommand = new Command("Screen", Command.SCREEN, 1);
        }
        return authStartCommand;
    }
    
    public Command get_authNextCommand() {
        if (authNextCommand == null) {
            authNextCommand = new Command("Next", Command.SCREEN, 1);
        }
        return authNextCommand;
    }
    
    public Command get_showManualUpdateCommand() {
        if (showManualUpdateCommand == null) {
            showManualUpdateCommand = new Command("Enter Loc", Command.SCREEN, 1);
        }
        return showManualUpdateCommand;
    }
    
    public Form get_enterLocForm() {
        if (enterLocForm == null) {
            enterLocForm = new Form("Update Location", new Item[] {
                get_locEntryField(),
                get_yourLoc()
            });
            enterLocForm.addCommand(get_submitUpdateCommand());
            enterLocForm.addCommand(get_homeCommand());
            enterLocForm.setCommandListener(this);
        }
        return enterLocForm;
    }
    
    public Command get_submitUpdateCommand() {
        if (submitUpdateCommand == null) {
            submitUpdateCommand = new Command("Submit", Command.SCREEN, 1);
        }
        return submitUpdateCommand;
    }
    
    public Command get_homeCommand() {
        if (homeCommand == null) {
            homeCommand = new Command("Back", Command.BACK, 1);
        }
        return homeCommand;
    }
    
    public StringItem get_stringItemCode() {
        if (stringItemCode == null) {
            stringItemCode = new StringItem("Please Wait...", "");
        }
        return stringItemCode;
    }
    
    public Spacer get_spacer1() {
        if (spacer1 == null) {
            spacer1 = new Spacer(1000, 1);
        }
        return spacer1;
    }
    
    public StringItem get_stringItemInstuctions() {
        if (stringItemInstuctions == null) {
            stringItemInstuctions = new StringItem("", "Please go to the URL below and enter the code above.  Hit \"Next\" when done");
        }
        return stringItemInstuctions;
    }
    
    public TextField get_locEntryField() {
        if (locEntryField == null) {
            locEntryField = new TextField("Your location:", null, 120, TextField.ANY);
        }
        return locEntryField;
    }
    
    public StringItem get_authURL() {
        if (authURL == null) {
            authURL = new StringItem("", "a URL");
        }
        return authURL;
    }
    
    public StringItem get_yourLoc() {
        if (yourLoc == null) {
            yourLoc = new StringItem("FE thinks you are at:", "unkonwn");
        }
        return yourLoc;
    }
    
    public Form get_gpsForm() {
        if (gpsForm == null) {
            gpsForm = new Form(null, new Item[] {
                get_coordStringItem(),
                get_feLocStringItem()
            });
            gpsForm.addCommand(get_startGPSCommand());
            gpsForm.addCommand(get_stopGPSCommand());
            gpsForm.setCommandListener(this);
            
            gpsForm.addCommand(get_homeCommand());
        }
        return gpsForm;
    }
    
    public Command get_startGPSCommand() {
        if (startGPSCommand == null) {
            startGPSCommand = new Command("Start GPS", Command.SCREEN, 1);
        }
        return startGPSCommand;
    }
    
    public Command get_stopGPSCommand() {
        if (stopGPSCommand == null) {
            stopGPSCommand = new Command("Stop GPS", Command.SCREEN, 1);
        }
        return stopGPSCommand;
    }
    
    public StringItem get_coordStringItem() {
        if (coordStringItem == null) {
            coordStringItem = new StringItem("Your coordinates", "GPS stopped");
        }
        return coordStringItem;
    }
    
    public StringItem get_feLocStringItem() {
        if (feLocStringItem == null) {
            feLocStringItem = new StringItem("FE thinks you\'re at:", "unknown");
        }
        return feLocStringItem;
    }
    
    public Command get_showGPSCommand() {
        if (showGPSCommand == null) {
            showGPSCommand = new Command("Do GPS Loc", Command.SCREEN, 1);
        }
        return showGPSCommand;
    }
    
}



class LocationRetriever extends Thread {
    private FEMidlet midlet;
    private LocationProvider lp;
    private boolean quit;
    public LocationRetriever(FEMidlet midlet) {
        /**
         * Constructor
         *
         * EFFECTS: Initialise the server and store midlet information
         *
         * @param midlet The main application midlet
         * @param server Forecast Server URL
         *
         */
        this.midlet = midlet;
        
        Criteria cr= new Criteria();
        cr.setHorizontalAccuracy(500);
        try {
            lp= LocationProvider.getInstance(cr);
        } catch (LocationException ex) {
            ex.printStackTrace();
        }
        
    }
    
    public boolean isRunning() {
        return (quit==false);
    }
    
    public void stop() {
        quit=true;
    }
    
    public void run() {
        /**
         * Entry point of the thread
         *
         * EFFECTS: call to connect() method
         */
        quit=false;
        
        while (!quit) {
            try {
                checkLocation();
            } catch (LocationException ex) {
                ex.printStackTrace();
                //midlet.displayString(ex.toString());
                midlet.updateLocation(0,0,"no GPS signal");
            } catch (InterruptedException ex) {
                ex.printStackTrace();
                //midlet.displayString(ex.toString());
                midlet.updateLocation(0,0,ex.toString());
            }
            try {
                sleep(150000); //150 seconds=2.5 minutes
            } catch (InterruptedException ignored) {
                
            }
        }
    }
        
    public void checkLocation() throws LocationException, InterruptedException {
        String string=null;
        Location l;
        //LocationProvider lp;
        Coordinates c;
        double lat=0;
        double lon=0;
        // Set criteria for selecting a location provider:
        // accurate to 500 meters horizontally
        //Criteria cr= new Criteria();
        //cr.setHorizontalAccuracy(500);
        // Get an instance of the provider
        //lp= LocationProvider.getInstance(cr);
        // Request the location, setting a one-minute timeout
        l = lp.getLocation(60);
        c = l.getQualifiedCoordinates();
        if(c != null ) {
            // Use coordinate information
            lat = c.getLatitude();
            lon = c.getLongitude();
            //string = "\nLatitude : " + lat + "\nLongitude : " + lon;
        } else {
            string ="Location API failed";
        }
        //midlet.displayString(string);
        midlet.updateLocation(lat, lon, string);
    }
}