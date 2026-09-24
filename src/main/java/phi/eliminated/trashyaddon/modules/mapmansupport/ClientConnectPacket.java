// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapmansupport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public record ClientConnectPacket(String path) implements MapmanServerboundPacket {
    @Override
    public ByteBuffer encode() {
        byte[] e = path.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(e.length + 4).order(ByteOrder.nativeOrder());
        buf.putInt(e.length);
        buf.put(e);
        return buf.flip();
    }
}
