package tw.nekomimi.nekogram.settings;

import android.app.assist.AssistContent;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;

import java.util.ArrayList;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.PopupHelper;

public class WsSettingsActivity extends BaseNekoSettingsActivity {

    private int descriptionRow;
    private int settingsRow;
    private int proxyProtocolRow;

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == proxyProtocolRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            arrayList.add("WireGuard");
            arrayList.add("WebSocket");
            PopupHelper.show(arrayList, "Protocol", NekoConfig.wireGuardProxy ? 0 : 1, getParentActivity(), view, i -> {
                NekoConfig.setWireGuardProxy(i == 0);
                listAdapter.notifyItemChanged(proxyProtocolRow);
                NekoConfig.restartProxy();
            });
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected String getActionBarTitle() {
        return NekoConfig.WS_ADDRESS;
    }

    @Override
    protected void updateRows() {
        rowCount = 0;

        settingsRow = rowCount++;
        proxyProtocolRow = rowCount++;
        descriptionRow = rowCount++;
    }

    @Override
    protected boolean hasWhiteActionBar() {
        return false;
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 2: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    if (position == proxyProtocolRow) {
                        String value = NekoConfig.wireGuardProxy ? "WireGuard" : "WebSocket";
                        textCell.setTextAndValue("Protocol", value, false);
                    }
                    break;
                }
                case 4: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == settingsRow) {
                        headerCell.setText(LocaleController.getString("Settings", R.string.Settings));
                    }
                    break;
                }
                case 7: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText("some description here");
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == descriptionRow) {
                return 7;
            } else if (position == settingsRow) {
                return 4;
            }
            return 2;
        }
    }

    @Override
    public void onProvideAssistContent(AssistContent outContent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            outContent.setWebUri(Uri.parse("https://nekogram.app/proxy"));
        }
    }
}
