// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapmansupport;

import java.nio.ByteBuffer;

public class RequestMapDataPacket implements MapmanClientboundPacket {
    public byte flag;
    public int id;

    @Override
    public void decode(ByteBuffer data) {
        flag = data.get();
        id = data.getInt();
    }
}
