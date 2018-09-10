/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.nrftoolbox.code;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import no.nordicsemi.android.nrftoolbox.R;
import no.nordicsemi.android.nrftoolbox.code.domain.Command;
import no.nordicsemi.android.nrftoolbox.code.domain.CodeConfiguration;

public class CODEControlFragment extends Fragment implements GridView.OnItemClickListener, CODEActivity.ConfigurationListener {
	private final static String TAG = "CODEControlFragment";
	private final static String SIS_EDIT_MODE = "sis_edit_mode";

	private CodeConfiguration mConfiguration;
	private CODEButtonAdapter mAdapter;
	private boolean mEditMode;

	@Override
	public void onAttach(final Context context) {
		super.onAttach(context);

		try {
			((CODEActivity)context).setConfigurationListener(this);
		} catch (final ClassCastException e) {
			Log.e(TAG, "The parent activity must implement EditModeListener");
		}
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			mEditMode = savedInstanceState.getBoolean(SIS_EDIT_MODE);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		((CODEActivity)getActivity()).setConfigurationListener(null);
	}

	@Override
	public void onSaveInstanceState(final Bundle outState) {
		outState.putBoolean(SIS_EDIT_MODE, mEditMode);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.fragment_feature_code_control, container, false);

		final GridView grid = view.findViewById(R.id.grid);
		grid.setAdapter(mAdapter = new CODEButtonAdapter(mConfiguration));
		grid.setOnItemClickListener(this);
		mAdapter.setEditMode(mEditMode);

		return view;
	}

	@Override
	public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
		if (mEditMode) {
			Command command = mConfiguration.getCommands()[position];
			if (command == null)
				mConfiguration.getCommands()[position] = command = new Command();
			final CODEEditDialog dialog = CODEEditDialog.getInstance(position, command);
			dialog.show(getChildFragmentManager(), null);
		} else {
			final Command command 		 = (Command)mAdapter.getItem(position);
			final Command.Protocol eproto = command.getProtocol();
			final Command.Eol eol 		 = command.getEol();
			final Command.DeviceType dev = command.getDeviceType();
			int devInt = 1 << 30;
			byte protoByte = 0;
			switch( eproto )
			{
				case ack:  protoByte = 0; break;
				case data: protoByte = 1; break;
				case cmd:  protoByte = 2; break;
				//default: devInt = 0;
			}
			switch( dev )
			{
				case reader: devInt = 0 << 30; break;
				case host:   devInt = 1 << 30; break;
				case modem:  devInt = 2 << 30; break;
				default:
					devInt = 0;
					break;
			}
			String text = command.getCommand();
			if (text == null)
				text = "";
			final int maxLen = 182 - 3;
			if (text.length() > (maxLen - 0x11))
			    text = "Too Long" + text.substring(0,(maxLen-8 - 0x11));
			final byte [] header  = {(byte)1, (byte)'C', (byte)'T', (byte)'1'};
			int   xLen = text.length();
            final byte [] dstAddr = intToBytes( devInt | 0x0FFFFFFF );
            final byte [] srcAddr = intToBytes(0x40000000);
            final byte [] proto   = {0x01};
            final byte [] flags     = {0x00};
            final byte [] protocol  = {protoByte};
            final byte [] ack       = {0x00, 0x00};
            final byte [] xAction   = {0x00, 0x01};
            final byte [] cmdID    = {(byte)0x80, 0x04};
            final byte [] payload = combineByteArrays(flags, protocol, ack, xAction, cmdID, stringToASCIIBytes(text));
            final int len = payload.length + 11;
            final short xxLen = (short)len;
            final byte [] length  = shortToBytes(xxLen);
			final byte [] crc     = crc16(combineByteArrays(                dstAddr, srcAddr, proto, payload     ));
			final byte [] packet  =       combineByteArrays(header, length, dstAddr, srcAddr, proto, payload, crc);
			switch (eol) {
				case CR_LF:
					text = text.replaceAll("\n", "\r\n");
					break;
				case CR:
					text = text.replaceAll("\n", "\r");
					break;
			}
			final CODEInterface code = (CODEInterface) getActivity();
            code.send(packet);
		}
	}

	@Override
	public void onConfigurationModified() {
		mAdapter.notifyDataSetChanged();
	}

	@Override
	public void onConfigurationChanged(final CodeConfiguration configuration) {
		mConfiguration = configuration;
		mAdapter.setConfiguration(configuration);
	}

	@Override
	public void setEditMode(final boolean editMode) {
		mEditMode = editMode;
		mAdapter.setEditMode(mEditMode);
	}

    /**
     * Get bytes representing ASCII string
     *
     * @param input - String to be converted
     * @return byte array if successful, null otherwise
     */
    public static byte[] stringToASCIIBytes(String input) {
        try {
            return input.getBytes("US-ASCII");
        } catch (java.io.UnsupportedEncodingException e) {
            return null;
        }
    }

    public static byte[] intToBytes(int num) {
        return new byte[]{(byte) (num >>> 24), (byte) (num >>> 16), (byte) (num >>> 8), (byte) num};
    }

    public static byte[] shortToBytes(short num) {
        return new byte[]{(byte) (num >>> 8), (byte) num};
    }

    /**
     * Combine arrays in order
     *
     * @param inputs - byte arrays in order of combining
     * @return combination of all inputs
     */
    public static byte[] combineByteArrays(byte[]... inputs) {
        int len = 0;
        for (byte[] arr : inputs) {
            len += arr.length;
        }
        byte[] result = new byte[len];
        int index = 0;
        for (byte[] arr : inputs) {
            System.arraycopy(arr, 0, result, index, arr.length);
            index += arr.length;
        }
        return result;
    }

    /**
     * Calculate and return CRC16
     *
     * @param data - bits for calculation
     * @return two byte CRC16
     */
    public static byte[] crc16(byte[] data) {
        // translated from C found in CortexTools\trunk\CodeMasterSuite\MultiCodeUtil\Common\Src\crc16.c

        final int crcBits = 16;
        final int charBits = 8;
        final int diffBits = crcBits - charBits;
        int c = 0;

        for (int i = 0; i < data.length; i++) {
            c = (c << charBits) ^ crctab[((c >> diffBits) ^ data[i]) & 0xff];
        }

        byte[] result = new byte[2];
        result[0] = (byte) ((c >> charBits) & 0xff);
        result[1] = (byte) (c & 0xff);
        return result;
    }

    private static final int crctab[] =
    {
		0x00000000,  0x00001021,  0x00002042,  0x00003063,  0x00004084,  0x000050a5,  0x000060c6,  0x000070e7,
		0x00008108,  0x00009129,  0x0000a14a,  0x0000b16b,  0x0000c18c,  0x0000d1ad,  0x0000e1ce,  0x0000f1ef,
		0x00001231,  0x00000210,  0x00003273,  0x00002252,  0x000052b5,  0x00004294,  0x000072f7,  0x000062d6,
		0x00009339,  0x00008318,  0x0000b37b,  0x0000a35a,  0x0000d3bd,  0x0000c39c,  0x0000f3ff,  0x0000e3de,
		0x00002462,  0x00003443,  0x00000420,  0x00001401,  0x000064e6,  0x000074c7,  0x000044a4,  0x00005485,
		0x0000a56a,  0x0000b54b,  0x00008528,  0x00009509,  0x0000e5ee,  0x0000f5cf,  0x0000c5ac,  0x0000d58d,
		0x00003653,  0x00002672,  0x00001611,  0x00000630,  0x000076d7,  0x000066f6,  0x00005695,  0x000046b4,
		0x0000b75b,  0x0000a77a,  0x00009719,  0x00008738,  0x0000f7df,  0x0000e7fe,  0x0000d79d,  0x0000c7bc,
		0x000048c4,  0x000058e5,  0x00006886,  0x000078a7,  0x00000840,  0x00001861,  0x00002802,  0x00003823,
		0x0000c9cc,  0x0000d9ed,  0x0000e98e,  0x0000f9af,  0x00008948,  0x00009969,  0x0000a90a,  0x0000b92b,
		0x00005af5,  0x00004ad4,  0x00007ab7,  0x00006a96,  0x00001a71,  0x00000a50,  0x00003a33,  0x00002a12,
		0x0000dbfd,  0x0000cbdc,  0x0000fbbf,  0x0000eb9e,  0x00009b79,  0x00008b58,  0x0000bb3b,  0x0000ab1a,
		0x00006ca6,  0x00007c87,  0x00004ce4,  0x00005cc5,  0x00002c22,  0x00003c03,  0x00000c60,  0x00001c41,
		0x0000edae,  0x0000fd8f,  0x0000cdec,  0x0000ddcd,  0x0000ad2a,  0x0000bd0b,  0x00008d68,  0x00009d49,
		0x00007e97,  0x00006eb6,  0x00005ed5,  0x00004ef4,  0x00003e13,  0x00002e32,  0x00001e51,  0x00000e70,
		0x0000ff9f,  0x0000efbe,  0x0000dfdd,  0x0000cffc,  0x0000bf1b,  0x0000af3a,  0x00009f59,  0x00008f78,
		0x00009188,  0x000081a9,  0x0000b1ca,  0x0000a1eb,  0x0000d10c,  0x0000c12d,  0x0000f14e,  0x0000e16f,
		0x00001080,  0x000000a1,  0x000030c2,  0x000020e3,  0x00005004,  0x00004025,  0x00007046,  0x00006067,
		0x000083b9,  0x00009398,  0x0000a3fb,  0x0000b3da,  0x0000c33d,  0x0000d31c,  0x0000e37f,  0x0000f35e,
		0x000002b1,  0x00001290,  0x000022f3,  0x000032d2,  0x00004235,  0x00005214,  0x00006277,  0x00007256,
		0x0000b5ea,  0x0000a5cb,  0x000095a8,  0x00008589,  0x0000f56e,  0x0000e54f,  0x0000d52c,  0x0000c50d,
		0x000034e2,  0x000024c3,  0x000014a0,  0x00000481,  0x00007466,  0x00006447,  0x00005424,  0x00004405,
		0x0000a7db,  0x0000b7fa,  0x00008799,  0x000097b8,  0x0000e75f,  0x0000f77e,  0x0000c71d,  0x0000d73c,
		0x000026d3,  0x000036f2,  0x00000691,  0x000016b0,  0x00006657,  0x00007676,  0x00004615,  0x00005634,
		0x0000d94c,  0x0000c96d,  0x0000f90e,  0x0000e92f,  0x000099c8,  0x000089e9,  0x0000b98a,  0x0000a9ab,
		0x00005844,  0x00004865,  0x00007806,  0x00006827,  0x000018c0,  0x000008e1,  0x00003882,  0x000028a3,
		0x0000cb7d,  0x0000db5c,  0x0000eb3f,  0x0000fb1e,  0x00008bf9,  0x00009bd8,  0x0000abbb,  0x0000bb9a,
		0x00004a75,  0x00005a54,  0x00006a37,  0x00007a16,  0x00000af1,  0x00001ad0,  0x00002ab3,  0x00003a92,
		0x0000fd2e,  0x0000ed0f,  0x0000dd6c,  0x0000cd4d,  0x0000bdaa,  0x0000ad8b,  0x00009de8,  0x00008dc9,
		0x00007c26,  0x00006c07,  0x00005c64,  0x00004c45,  0x00003ca2,  0x00002c83,  0x00001ce0,  0x00000cc1,
		0x0000ef1f,  0x0000ff3e,  0x0000cf5d,  0x0000df7c,  0x0000af9b,  0x0000bfba,  0x00008fd9,  0x00009ff8,
		0x00006e17,  0x00007e36,  0x00004e55,  0x00005e74,  0x00002e93,  0x00003eb2,  0x00000ed1,  0x00001ef0,
    };
}
