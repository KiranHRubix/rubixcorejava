package com.rubix.Resources;

import com.rubix.TokenTransfer.TokenSender;
import io.ipfs.api.IPFS;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;

import static com.rubix.Resources.Functions.*;
import static com.rubix.Resources.IPFSNetwork.executeIPFSCommands;

public class APIHandler {
    private static final Logger APILogger = Logger.getLogger(APIHandler.class);
    public static IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/" + IPFS_PORT);


    /**
     * Initiates a transfer between two nodes
     * @param tokens List of tokens
     * @param recDID DID of the receiver
     * @param comments Any extra comment for the application
     * @param tokenHeader List of Banks for each tokens
     * @return Message from the sender with transaction details
     * @throws JSONException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public static JSONObject send(JSONArray tokens, String recDID, String comments, JSONArray tokenHeader) throws JSONException, NoSuchAlgorithmException, IOException {
        Functions.pathSet();
        PropertyConfigurator.configure(LOGGER_PATH + "log4jWallet.properties");
        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        String senDID = getValues(DATA_PATH + "DID.json", "didHash", "peerid", senderPeerID);

        JSONObject sendMessage = new JSONObject();
        if (recDID.length() != 46) {
            sendMessage.put("did", senDID);
            sendMessage.put("tid", "null");
            sendMessage.put("status", "Failed");
            sendMessage.put("message", "Invalid Receiver Did Entered");
            return sendMessage;
        }

        if (tokens.length() < 1) {
            sendMessage.put("did", senDID);
            sendMessage.put("tid", "null");
            sendMessage.put("status", "Failed");
            sendMessage.put("message", "Invalid amount");
            return sendMessage;
        }
        sendMessage =  TokenSender.Send(tokens, recDID, DATA_PATH + senDID + "/PrivateShare.png", ipfs, SEND_PORT, comments, tokenHeader);
        APILogger.info(sendMessage);
        return sendMessage;
    }

    /**
     * A call to get details of a transaction given its ID
     * @param txnId
     * @return Transaction Details
     * @throws JSONException
     */
    public static String transactionDetail(String txnId) throws JSONException {
        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        String result = "";
        if (transactionHistory.length() == 0)
            return "No transactions found";

        JSONArray transArray = new JSONArray(transactionHistory);
        JSONObject obj = new JSONObject();
        for (int i = 0; i < transArray.length(); i++) {
            obj = transArray.getJSONObject(i);
            if (obj.get("txn").equals(txnId))
                result =  obj.toString();
        }
        APILogger.info("Transaction Details for : " + obj.toString());
        return result;
    }

    /**
     * A call to get the account information
     * @return Detailed explanation of the account information of the user
     * @throws JSONException
     */
    public static String accountInformation() throws JSONException {
        int txnAsSender = 0, txnAsReceiver = 0;

        JSONObject objectParser;
        JSONObject accountDetails = new JSONObject();
        File fileCheck1 = new File(WALLET_DATA_PATH + "TransactionHistory.json");
        if (!fileCheck1.exists()) {
            return "Transaction History file not found";
        }

        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray transArray = new JSONArray(transactionHistory);

        if(!(transArray.length() == 0)){
            for (int i = 0; i < transArray.length(); i++) {
                objectParser = transArray.getJSONObject(i);
                if (objectParser.get("role").equals("Sender"))
                    txnAsSender++;
                else txnAsReceiver++;
            }
        }

        int balance = accountBalance();
        String senderPeerID = getPeerID(DATA_PATH + "DID.json");
        String did = getValues(DATA_PATH + "DID.json", "didHash", "peerid", senderPeerID);
        String wid = getValues(DATA_PATH + "DID.json", "walletHash", "peerid", senderPeerID);
        accountDetails.put("did", did);
        accountDetails.put("wid", wid);
        accountDetails.put("balance", balance);
        accountDetails.put("senderTxn", txnAsSender);
        accountDetails.put("receiverTxn", txnAsReceiver);
        return accountDetails.toString();
    }

    /**
     * A call to get account balance
     * @return Balance
     */
    public static int accountBalance(){
        Functions.pathSet();
        File folder = new File(TOKENS_PATH);
        File[] listOfFiles = folder.listFiles();
        return listOfFiles.length;
    }

    /**
     * A call to sync all the nodes in the network
     * @return Message if failed or succeeded
     * @throws IOException
     */
    public static String sync() throws IOException {
        StringBuilder result = new StringBuilder();
        int syncFlag = 0;
        URL url = new URL(SYNC_IP + "/get");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;

        while ((line = rd.readLine()) != null) {
            result.append(line);
            syncFlag = 1;
        }

        rd.close();
        writeToFile(DATA_PATH + "DataTable.json", result.toString(), false);
        if (syncFlag == 1) {
            return "Synced Successfully!";
        } else
            return "Not synced! Try again after sometime.";
    }

    /**
     * A call the get the token history
     * @param token Token
     * @return Proof history of the token
     */
    public static String proofChains(String token){

        if (token.length() != 46 || !token.startsWith("Qm"))
            return "Incorrect token entered";

        File tokensFolder = new File(TOKENCHAIN_PATH);
        if (!tokensFolder.exists())
            return "Proof chain does not exist";
        File[] listOfTokens = tokensFolder.listFiles();
        String proof = null;

        for (File listOfToken : listOfTokens) {
            if (listOfToken.isFile()) {
                if (listOfToken.getName().substring(0, 46).equals(token)) {
                    proof = readFile(TOKENCHAIN_PATH + listOfToken.getName());
                    break;
                }
            }
        }
        if (proof == null)
            return "token proof not found";
        else
            return proof;
    }

    /**
     * A call to get list transactions between two mentioned dates
     * @param s Start Date
     * @param e End Date
     * @return List of transactions
     * @throws JSONException
     */
    public static String transactionsByDate(String s, String e) throws JSONException {
        LocalDate start = LocalDate.parse(s);
        LocalDate end = LocalDate.parse(e);
        ArrayList<LocalDate> totalDates = new ArrayList<>();
        while (!start.isAfter(end)) {
            totalDates.add(start);
            start = start.plusDays(1);
        }


        File fileCheck1 = new File(WALLET_DATA_PATH + "TransactionHistory.json");
        if (!fileCheck1.exists()) {
            return "transactionHistory file not found";
        }
        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");


        JSONArray transArray = new JSONArray(transactionHistory);

        if (transArray.length() == 0)
            return "No Transactions found";

        JSONArray reqArray = new JSONArray();
        int count = 0;
        JSONObject Obj;
        String temp;
        while (count < totalDates.size()) {
            temp = totalDates.get(count).toString();
            for (int i = 0; i < transArray.length(); i++) {
                Obj = transArray.getJSONObject(i);
                if (temp.equals(Obj.get("Date")))
                    reqArray.put(Obj);
            }
            count++;
        }
        return reqArray.toString();
    }

    /**
     * A call to get list transactions within a mentioned count
     * @param n Count
     * @return List of transactions
     * @throws JSONException
     */
    public static String transactionsByCount(int n) throws JSONException {
        int count = 0, found = 0;
        if (n <= 0)
            return "Count can't be null or negative";


        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        Date date = new Date();

        ArrayList<LocalDate> totalDates = new ArrayList<LocalDate>();
        LocalDate todayDate = LocalDate.parse(dateFormat.format(date).replace("/", "-"));


        File fileCheck1 = new File(WALLET_DATA_PATH + "TransactionHistory.json");
        if (!fileCheck1.exists()) {
            return "Transaction History file not found";
        }
        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray transArray = new JSONArray(transactionHistory);
        if (transArray.length() == 0)
            return "No Transactions found";

        JSONArray reqArray = new JSONArray();
        count = 0;
        if (n >= transArray.length())
            return transArray.toString();

        String temp;
        JSONObject Obj;


        totalDates.add(todayDate);

        while (found < n) {
            temp = totalDates.get(count).toString();
            for (int i = 0; i < transArray.length(); i++) {
                Obj = transArray.getJSONObject(i);
                if (temp.equals(Obj.get("Date")) && found < n) {
                    reqArray.put(Obj);
                    found++;
                }

            }
            if (found >= n)
                break;

            count++;
            todayDate = todayDate.minusDays(1);
            totalDates.add(todayDate);
        }
        return reqArray.toString();
    }

    /**
     * A call to close all open IPFS streams
     */
    public static void closeStreams(){
        executeIPFSCommands(" ipfs p2p close --all");
    }

    /**
     * A call to get list transactions with the mentioned comment
     * @param comment Comment
     * @return List of transactions
     * @throws JSONException
     */
    public static String transactionsByComment(String comment) throws JSONException {

        String transactionHistory = readFile(WALLET_DATA_PATH + "TransactionHistory.json");
        JSONArray transArray = new JSONArray(transactionHistory);
        JSONObject obj = new JSONObject();
        for (int i = 0; i < transArray.length(); i++) {
            obj = transArray.getJSONObject(i);
            if (obj.get("comment").equals(comment))
                return obj.toString();
        }

        return "Not found any details for comment : " + comment;
    }
}
