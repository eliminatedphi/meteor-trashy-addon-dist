// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapmansupport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public record MapFoundFramePacket(int id, String customName, boolean locked, int scale, byte[] data) implements MapmanServerboundPacket {
    @Override
    public ByteBuffer encode() {
        byte[] encName = customName.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b;
        if (data != null) {
            b = ByteBuffer.allocate(9 + encName.length + 16384).order(ByteOrder.nativeOrder());
            int v = encName.length;
            if (locked) v |= 0x8000_0000;
            if (scale >= 0 && scale < 5) v |= (scale << 28);
            b.putInt(id);
            b.put((byte) 1);
            b.putInt(v);
            b.put(encName);
            b.put(data);
        } else {
            b = ByteBuffer.allocate(5).order(ByteOrder.nativeOrder());
            b.putInt(id);
            b.put((byte) 0);
        }
        b.flip();
        return b;
    }
}
