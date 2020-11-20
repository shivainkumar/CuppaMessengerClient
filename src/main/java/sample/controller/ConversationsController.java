package sample.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import sample.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ConversationsController {

    @FXML
    VBox conversationVbox;
    @FXML
    Gson gson = new Gson();

    static HashMap<String, ConversationWindowController> conversationPanes = new HashMap<>();
    static HashMap<String, Stage> conversationStage = new HashMap<>();
    static HashMap<String, Conversation> conversationHashMap;
    static Path backupFile = Path.of("backup.cuppa");

    UserList users = UserList.getInstance();
    Client client = Client.getInstance();

    public ConversationsController() throws IOException {
    }

    @FXML
    public void initialize() throws IOException {

        if(conversationHashMap == null){
            conversationHashMap = new HashMap<>();
        }

        if(client.isAuth()){
            loadConvoFromFile();
            generateConversationTiles();
        }
    }

    public String generateKey(Message msg){
        List<String> participantsList = new ArrayList<>();
        participantsList.add(msg.to);
        participantsList.add(msg.from);
        Collections.sort(participantsList);
        return participantsList.toString();
    }

    public String generateKey(ArrayList<String> participants){
        Collections.sort(participants);
        return participants.toString();
    }

    public void saveConvoToFile() throws IOException {
        Files.writeString(backupFile, gson.toJson(conversationHashMap));
    }

    public void loadConvoFromFile() throws IOException {
        Type type = new TypeToken<HashMap<String, Conversation>>(){}.getType();
        HashMap<String, Conversation> loadedConvo = gson.fromJson(Files.readString(backupFile), type);
        if(loadedConvo != null){
            conversationHashMap = loadedConvo;
        }
    }

    public ConversationWindowController createConversationWindow(Conversation convo) throws IOException{

        String key = generateKey((ArrayList<String>) convo.getParticipants());

        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/mainPage/conversations/conversationWindow.fxml"));
        Parent conversationWindowP = loader.load();
        ConversationWindowController convoWindowController = loader.getController();


        ArrayList<User> others = new ArrayList<>();
        for(String user: convo.getParticipants()){

            if(user.equals(client.getUser().getUsername()))
                continue;

            others.add(users.getUser(user));
        }

        convoWindowController.setInfo(others, convo.getName());


        Scene chatScene = new Scene(conversationWindowP);
        Stage chatStage = new Stage();
        chatStage.setScene(chatScene);

        conversationStage.put(key, chatStage);
        conversationPanes.put(key, convoWindowController);

        return convoWindowController;
    }

    public void floodConversationPane(String key) throws IOException {
        if(conversationHashMap.containsKey(key)) {
            ConversationWindowController window = conversationPanes.get(key);
            Conversation convo = conversationHashMap.get(key);

            for (Message msg : convo.getMessages()) {
                window.addMessageToPane(msg);
            }

        }
    }


    public void openExistingConversationPane(String key){
        Stage pane = conversationStage.get(key);
        pane.show();
    }

    public boolean doesConversationPaneNotExist(String key){

        return !conversationPanes.containsKey(key);
    }

    public boolean doesConversationExist(String key){

        return conversationHashMap.containsKey(key);
    }

    public void addReceivedMessage(Message msg) throws IOException {
        ArrayList<String> participants = new ArrayList<>();
        String convoName;
        if(msg.subject.contains("user_to_group:")){
            String[] recipients = gson.fromJson(msg.to, String[].class);
            for(String rec : recipients){
                participants.add(rec);
            }

            convoName = msg.subject.replace("user_to_group:", "");
        }
        else{
            participants.add(msg.to);
            convoName = "default";
        }

        participants.add(msg.from);

        String key = generateKey(participants);

        ConversationWindowController window;
        if(!conversationHashMap.containsKey(key)){
            if(participants.size() > 2){
                createConversation(participants, convoName);
            }
            else{
                createConversation(msg);
            }

        }

        conversationHashMap.get(key).addMessage(msg);

        if(conversationPanes.containsKey(key)){
            window = conversationPanes.get(key);
        }
        else{
           window = createConversationWindow(conversationHashMap.get(key));
        }

        window.addMessageToPane(msg);

        saveConvoToFile();
    }

    public void createConversation(Message msg){
        ArrayList<String> participants = new ArrayList<>();
        participants.add(msg.to);
        participants.add(msg.from);

        Conversation convo = new Conversation(participants);
        conversationHashMap.put(generateKey(msg), convo);

    }

    public Conversation createConversation(ArrayList<String> participants, String name){

        String key = generateKey(participants);
        Conversation convo = new Conversation(participants);
        convo.setName(name);

        conversationHashMap.put(key, convo);

        return convo;
    }

    public Conversation getConversation(String key){
        return conversationHashMap.get(key);
    }


    public void addMessageToConversation(Message msg)
    {


        ArrayList<String> participants = new ArrayList<>();
        String[] recipients = gson.fromJson(msg.to, String[].class);
        for(String rec : recipients){
            participants.add(rec);
        }
        participants.add(msg.from);

        String key = generateKey(participants);
        conversationHashMap.get(key).addMessage(msg);
    }


    private void generateConversationTiles() throws IOException {
        for (Map.Entry<String, Conversation> entry : conversationHashMap.entrySet()) {

            FXMLLoader convoTileloader = new FXMLLoader();
            convoTileloader.setLocation(getClass().getResource("/mainPage/conversations/conversationTile.fxml"));
            Parent conversationTile = convoTileloader.load();
            ConversationTileController convoTileController = convoTileloader.getController();

            Conversation convo = entry.getValue();

            String title;
            String avatar = "";
            if(convo.getName().equals("default") && convo.getParticipants().size() == 2){
                String other = "";
                for(String user: convo.getParticipants()){
                    if(!user.equals(client.getUser().getUsername())){
                        other = user;
                        break;
                    }
                }

                User otherUser = users.getUser(other);
                avatar = otherUser.getAvatar();
                if(otherUser != null){
                    title = otherUser.getFullName();
                }
                else{
                    title = other;
                }

            }
            else{
                title = convo.getName();
            }

            String subtitle = "";
            if(convo.getMessages().size() > 0) {
               subtitle = convo.getMessages().get(convo.getMessages().size() - 1).message;
            }



            convoTileController.setConversationInfo(this, entry.getKey(), convo, title, subtitle, avatar);
            conversationVbox.getChildren().add(conversationTile);
        }

    }


}
