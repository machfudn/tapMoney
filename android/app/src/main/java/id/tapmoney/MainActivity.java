package id.tapmoney;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import id.tapmoney.plugin.IsoDepNfcPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(IsoDepNfcPlugin.class);
        super.onCreate(savedInstanceState);
    }
}

