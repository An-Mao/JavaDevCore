package anmao.dev.core.json;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;

public class JsonConfig<T> extends JsonHelper{
    protected final String filePath;
    protected final String defaultData;
    protected final Type type;
    protected T datas;
    public JsonConfig(String filePath,String defaultData,TypeToken<T> typeToken){
        this.filePath = filePath;
        this.defaultData = defaultData;
        this.type = typeToken.getType();
        init();
    }
    public void init(){
        File file = new File(filePath);
        if (!file.exists()){
            reset();
        }else {
            CheckData(defaultData,filePath);
        }
        load();
    }
    public void reset(){
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(defaultData);
        } catch (IOException e) {
            throw new RuntimeException(e);
            //LOGGER.error(e.getMessage());
        }
    }
    private void load(){
        datas = null;
        Gson gson = new Gson();
        try (Reader reader = new FileReader(filePath)) {
            datas = gson.fromJson(reader, this.type);
        } catch (Exception e) {
            throw new RuntimeException(e);
            //LOGGER.error(e.getMessage());
        }
    }
    public T getDatas(){
        return datas;
    }
}
