// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapmansupport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public record MapFoundContainerPacket(List<Integer> ids) implements MapmanServerboundPacket{
    @Override
    public ByteBuffer encode() {
        ByteBuffer b = ByteBuffer.allocate(4 + 4 * ids.size()).order(ByteOrder.nativeOrder());
        b.putInt(ids.size());
        ids.forEach(b::putInt);
        b.flip();
        return b;
    }
}
