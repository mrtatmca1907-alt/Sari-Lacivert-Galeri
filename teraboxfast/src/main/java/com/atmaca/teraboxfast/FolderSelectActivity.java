package com.atmaca.teraboxfast;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.*;

public class FolderSelectActivity extends Activity {
    private IndexDb db;
    private ArrayList<String> folders;
    private ListView list;
    private TextView status;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        db=new IndexDb(this);
        folders=db.getFolders();

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(18,18,18,18);

        status=new TextView(this);
        status.setTextSize(18);
        status.setTextColor(Color.WHITE);
        status.setPadding(18,18,18,18);
        status.setBackgroundColor(Color.rgb(7,26,82));
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        Button all=new Button(this); all.setText("TÜMÜNÜ SEÇ");
        all.setOnClickListener(v->{db.chooseAll(true);applyChecks();updateStatus();});
        bar.addView(all,new LinearLayout.LayoutParams(0,-2,1));

        Button none=new Button(this); none.setText("TÜMÜNÜ KALDIR");
        none.setOnClickListener(v->{db.chooseAll(false);applyChecks();updateStatus();});
        bar.addView(none,new LinearLayout.LayoutParams(0,-2,1));

        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));

        list=new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_multiple_choice,folders));
        list.setOnItemClickListener((p,v,pos,id)->{
            db.setChosen(folders.get(pos),list.isItemChecked(pos));
            updateStatus();
        });
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));

        setContentView(root);
        applyChecks();
        updateStatus();
    }

    private void applyChecks(){
        HashSet<String> chosen=db.getChosen();
        for(int i=0;i<folders.size();i++)list.setItemChecked(i,chosen.contains(folders.get(i)));
    }

    private void updateStatus(){
        status.setText("ATMACA SEÇİMİ\nBulunan: "+db.folderCount()+"   Seçilen: "+db.chosenCount());
    }
}
