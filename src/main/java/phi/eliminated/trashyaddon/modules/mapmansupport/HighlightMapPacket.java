// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapmansupport;

import java.nio.ByteBuffer;
import java.util.Vector;

public class HighlightMapPacket implements MapmanClientboundPacket {
    public byte flag;
    public int color;
    public Vector<Integer> mapid;

    public static byte FRAME = 1;
    public static byte ITEM = 2;
    public static byte REMOVE = 4;
    public static byte CLEAR = 8;

    @Override
    public void decode(ByteBuffer data) {
        flag = data.get();
        color = data.getInt();
        mapid = new Vector<>();
        int length = data.getInt();
        if (data.remaining() >= length * 4) {
            for (int i = 0; i < length; ++i)
                mapid.add(data.getInt());
        }
    }
}
