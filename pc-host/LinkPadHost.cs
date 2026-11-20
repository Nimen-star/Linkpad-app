// LinkPad PC Host - Native Windows Desktop Application (C# 5.0 / .NET Framework 4.5+)
// Compiles to a standalone Windows GUI executable (no console window, no Python required).
// Registers up to 4 real virtual Xbox 360 controllers (XInput) via ViGEmBus,
// with optional Keyboard scancode mode and permanent Trusted Device pairing.

using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Principal;
using System.Text;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: AssemblyTitle("LinkPad PC Host")]
[assembly: AssemblyDescription("LAN Wireless Gamepad Receiver for LinkPad Android App")]
[assembly: AssemblyConfiguration("")]
[assembly: AssemblyCompany("LinkPad")]
[assembly: AssemblyProduct("LinkPad PC Host")]
[assembly: AssemblyCopyright("Copyright (C) LinkPad")]
[assembly: AssemblyVersion("1.3.0.0")]
[assembly: AssemblyFileVersion("1.3.0.0")]
[assembly: ComVisible(false)]

namespace LinkPadHostApp
{
    internal static class Protocol
    {
        public const byte MAGIC = 0x4C;
        public const int BASE_SIZE = 16;
        public const int MAX_NAME_BYTES = 24;
        public const int MAX_PACKET_SIZE = BASE_SIZE + MAX_NAME_BYTES;

        public const int HOST_UDP_PORT = 47850;
        public const int BEACON_UDP_PORT = 47851;
        public const string MULTICAST_GROUP = "239.255.77.77";

        public const int MAX_PLAYERS = 4;
        public const int CLIENT_TIMEOUT_MS = 5500;
        public const int BEACON_INTERVAL_MS = 750;

        public const byte PKT_HELLO = 1;
        public const byte PKT_WELCOME = 2;
        public const byte PKT_REJECT = 3;
        public const byte PKT_INPUT = 4;
        public const byte PKT_PING = 5;
        public const byte PKT_PONG = 6;
        public const byte PKT_BYE = 7;
        public const byte PKT_BEACON = 8;

        public const byte HELLO_MODE_CODE = 0;
        public const byte HELLO_MODE_TRUSTED_FAVORITE = 2;

        public const byte REJECT_INVALID_CODE = 1;
        public const byte REJECT_HOST_FULL = 2;
        public const byte REJECT_UNTRUSTED = 4;

        public static readonly string[] ButtonNames = new string[]
        {
            "UP", "DOWN", "LEFT", "RIGHT",
            "A", "B", "X", "Y",
            "L1", "R1", "START", "SELECT",
            "L2", "R2", "L3", "R3"
        };

        public sealed class Packet
        {
            public byte Type;
            public int Slot;
            public int Sequence;
            public int ButtonMask;
            public byte Layout;
            public byte ExtraByte;
            public int IntPayload;
            public byte Lx;
            public byte Ly;
            public byte Rx;
            public byte Ry;
            public string DeviceName = "";

            public int ExtractClientId32()
            {
                return ((Lx & 0xFF) << 24)
                    | ((Ly & 0xFF) << 16)
                    | ((Rx & 0xFF) << 8)
                    | (Ry & 0xFF);
            }
        }

        public static byte[] Encode(Packet p)
        {
            byte[] nameBytes = new byte[0];
            if (!string.IsNullOrEmpty(p.DeviceName) && (p.Type == PKT_HELLO || p.Type == PKT_BEACON))
            {
                byte[] raw = Encoding.UTF8.GetBytes(p.DeviceName);
                int len = Math.Min(raw.Length, MAX_NAME_BYTES);
                nameBytes = new byte[len];
                Buffer.BlockCopy(raw, 0, nameBytes, 0, len);
            }

            byte[] buf = new byte[BASE_SIZE + nameBytes.Length];
            buf[0] = MAGIC;
            buf[1] = p.Type;
            buf[2] = (byte)(p.Slot & 0xFF);
            buf[3] = (byte)(p.Sequence & 0xFF);
            buf[4] = (byte)((p.ButtonMask >> 8) & 0xFF);
            buf[5] = (byte)(p.ButtonMask & 0xFF);
            buf[6] = p.Layout;
            buf[7] = p.ExtraByte;
            buf[8] = (byte)((p.IntPayload >> 24) & 0xFF);
            buf[9] = (byte)((p.IntPayload >> 16) & 0xFF);
            buf[10] = (byte)((p.IntPayload >> 8) & 0xFF);
            buf[11] = (byte)(p.IntPayload & 0xFF);
            buf[12] = p.Lx;
            buf[13] = p.Ly;
            buf[14] = p.Rx;
            buf[15] = p.Ry;
            if (nameBytes.Length > 0)
            {
                Buffer.BlockCopy(nameBytes, 0, buf, BASE_SIZE, nameBytes.Length);
            }
            return buf;
        }

        public static Packet Decode(byte[] data, int length)
        {
            if (data == null || length < BASE_SIZE || data.Length < length) return null;
            if (data[0] != MAGIC) return null;

            Packet p = new Packet();
            p.Type = data[1];
            p.Slot = data[2] & 0xFF;
            p.Sequence = data[3] & 0xFF;
            p.ButtonMask = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
            p.Layout = data[6];
            p.ExtraByte = data[7];
            p.IntPayload = ((data[8] & 0xFF) << 24)
                | ((data[9] & 0xFF) << 16)
                | ((data[10] & 0xFF) << 8)
                | (data[11] & 0xFF);
            p.Lx = data[12];
            p.Ly = data[13];
            p.Rx = data[14];
            p.Ry = data[15];
            if (length > BASE_SIZE)
            {
                p.DeviceName = Encoding.UTF8.GetString(data, BASE_SIZE, length - BASE_SIZE)
                    .Trim(' ', '\0', '\r', '\n', '\t');
            }
            return p;
        }

        public static string MaskToText(int mask, byte layout, byte lx, byte ly, byte rx, byte ry)
        {
            List<string> parts = new List<string>();
            for (int i = 0; i < ButtonNames.Length; i++)
            {
                if ((mask & (1 << i)) != 0) parts.Add(ButtonNames[i]);
            }
            sbyte sx = unchecked((sbyte)lx);
            sbyte sy = unchecked((sbyte)ly);
            sbyte srx = unchecked((sbyte)rx);
            sbyte sry = unchecked((sbyte)ry);
            if (Math.Abs((int)sx) > 12 || Math.Abs((int)sy) > 12)
            {
                parts.Add(string.Format("LS({0},{1})", sx, sy));
            }
            if (Math.Abs((int)srx) > 12 || Math.Abs((int)sry) > 12)
            {
                parts.Add(string.Format("RS({0},{1})", srx, sry));
            }
            if (parts.Count == 0)
            {
                return layout == 1 ? "DUAL STICK \u00B7 IDLE" : "RETRO \u00B7 IDLE";
            }
            return string.Join(" ", parts.ToArray());
        }
    }

    internal interface IControllerOutput : IDisposable
    {
        string DisplayName { get; }
        bool IsXboxActive { get; }
        void EnsureSlotConnected(int slot);
        void ApplyState(int slot, int prevMask, int newMask, byte prevLx, byte prevLy, byte prevRx, byte prevRy, byte lx, byte ly, byte rx, byte ry);
        void ReleaseSlot(int slot, int currentMask);
    }

    internal sealed class ViGEmXboxOutput : IControllerOutput
    {
        private const string NugetUrl = "https://api.nuget.org/v3-flatcontainer/nefarius.vigem.client/1.21.256/nefarius.vigem.client.1.21.256.nupkg";
        // Isolated VID/PIDs so joy.cpl never collides with a cached Cosmic Byte / 045E:028E driver entry.
        private const ushort LinkPadVendorId = 0x1209;
        private const ushort LinkPadProductBase = 0x4C50; // + slot => 0x4C51..0x4C54
        private const ushort CleanXboxVendorId = 0x045E;
        private const ushort CleanXboxProductId = 0x02A1;

        private readonly object _client;
        private readonly MethodInfo _createX360Default;
        private readonly MethodInfo _createX360WithIds;
        private readonly MethodInfo _setButtonState;
        private readonly MethodInfo _setAxisValue;
        private readonly MethodInfo _setSliderValue;
        private readonly MethodInfo _submitReport;
        private readonly MethodInfo _connectMethod;
        private readonly MethodInfo _disconnectMethod;
        private readonly object[] _buttonObjects;
        private readonly object[] _axisObjects;
        private readonly object[] _sliderObjects;
        private readonly bool _uniqueSlotNames;
        private readonly Dictionary<int, object> _pads = new Dictionary<int, object>();

        private ViGEmXboxOutput(
            object client,
            MethodInfo createX360Default,
            MethodInfo createX360WithIds,
            MethodInfo setButtonState,
            MethodInfo setAxisValue,
            MethodInfo setSliderValue,
            MethodInfo submitReport,
            MethodInfo connectMethod,
            MethodInfo disconnectMethod,
            object[] buttonObjects,
            object[] axisObjects,
            object[] sliderObjects,
            bool uniqueSlotNames)
        {
            _client = client;
            _createX360Default = createX360Default;
            _createX360WithIds = createX360WithIds;
            _setButtonState = setButtonState;
            _setAxisValue = setAxisValue;
            _setSliderValue = setSliderValue;
            _submitReport = submitReport;
            _connectMethod = connectMethod;
            _disconnectMethod = disconnectMethod;
            _buttonObjects = buttonObjects;
            _axisObjects = axisObjects;
            _sliderObjects = sliderObjects;
            _uniqueSlotNames = uniqueSlotNames;
        }

        public string DisplayName
        {
            get { return _uniqueSlotNames ? "Xbox 360 Virtual Controller (LinkPad P1\u2013P4)" : "Xbox 360 Virtual Controller (XInput)"; }
        }
        public bool IsXboxActive { get { return true; } }

        private static void WriteOemName(ushort vid, ushort pid, string name)
        {
            string subKey = string.Format(
                @"System\CurrentControlSet\Control\MediaProperties\PrivateProperties\Joystick\OEM\VID_{0:X4}&PID_{1:X4}",
                vid,
                pid);
            try
            {
                using (RegistryKey k = Registry.CurrentUser.CreateSubKey(subKey))
                {
                    if (k != null) k.SetValue("OEMName", name, RegistryValueKind.String);
                }
            }
            catch { }
            try
            {
                using (RegistryKey k = Registry.LocalMachine.CreateSubKey(subKey))
                {
                    if (k != null) k.SetValue("OEMName", name, RegistryValueKind.String);
                }
            }
            catch { }
        }

        private static void EnsureCustomOemNames(bool uniqueSlotNames)
        {
            if (uniqueSlotNames)
            {
                for (int slot = 1; slot <= Protocol.MAX_PLAYERS; slot++)
                {
                    WriteOemName(LinkPadVendorId, (ushort)(LinkPadProductBase + slot), "LinkPad P" + slot);
                }
            }
            else
            {
                WriteOemName(CleanXboxVendorId, CleanXboxProductId, "LinkPad Controller (XInput)");
            }
        }

        private object CreateTargetForSlot(int slot)
        {
            EnsureCustomOemNames(_uniqueSlotNames);
            if (_createX360WithIds != null)
            {
                ushort vid = _uniqueSlotNames ? LinkPadVendorId : CleanXboxVendorId;
                ushort pid = _uniqueSlotNames ? (ushort)(LinkPadProductBase + slot) : CleanXboxProductId;
                return _createX360WithIds.Invoke(_client, new object[] { vid, pid });
            }
            return _createX360Default.Invoke(_client, null);
        }

        public static bool IsDriverInstalled()
        {
            try
            {
                using (RegistryKey key = Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Services\ViGEmBus"))
                {
                    if (key != null) return true;
                }
            }
            catch { }
            return false;
        }

        public static string GetDllPath()
        {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string localDll = Path.Combine(baseDir, "Nefarius.ViGEm.Client.dll");
            if (File.Exists(localDll)) return localDll;
            string appDataDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "LinkPad");
            return Path.Combine(appDataDir, "Nefarius.ViGEm.Client.dll");
        }

        public static void ConfigureSecurityProtocol()
        {
            try
            {
                ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
            }
            catch { }
            try { ServicePointManager.Expect100Continue = false; } catch { }
        }

        public static bool DownloadFileManaged(
            string url,
            string destPath,
            int minBytes,
            byte[] magicPrefix,
            Action<int> onProgress,
            out string error)
        {
            error = "";
            string dir = Path.GetDirectoryName(destPath);
            if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir)) Directory.CreateDirectory(dir);
            string partPath = destPath + ".part";

            try { if (File.Exists(partPath)) File.Delete(partPath); } catch { }

            try
            {
                ConfigureSecurityProtocol();
                HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
                req.Method = "GET";
                req.AllowAutoRedirect = true;
                req.MaximumAutomaticRedirections = 10;
                req.UserAgent = "LinkPadHost/1.2 (Windows NT)";
                req.Timeout = 30000;
                req.ReadWriteTimeout = 30000;

                using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
                using (Stream input = resp.GetResponseStream())
                using (FileStream output = new FileStream(partPath, FileMode.Create, FileAccess.Write, FileShare.None))
                {
                    long totalBytes = resp.ContentLength;
                    long received = 0;
                    int lastPct = -1;
                    byte[] buffer = new byte[16384];
                    int read;

                    while ((read = input.Read(buffer, 0, buffer.Length)) > 0)
                    {
                        output.Write(buffer, 0, read);
                        received += read;
                        if (onProgress != null && totalBytes > 0)
                        {
                            int pct = (int)((received * 100L) / totalBytes);
                            if (pct > 100) pct = 100;
                            if (pct != lastPct)
                            {
                                lastPct = pct;
                                onProgress(pct);
                            }
                        }
                    }
                }

                if (IsValidDownload(partPath, minBytes, magicPrefix))
                {
                    if (File.Exists(destPath)) File.Delete(destPath);
                    File.Move(partPath, destPath);
                    if (onProgress != null) onProgress(100);
                    return true;
                }
                error = "Downloaded file failed integrity check.";
            }
            catch (Exception ex)
            {
                error = ex.Message;
            }

            try { if (File.Exists(partPath)) File.Delete(partPath); } catch { }
            if (string.IsNullOrEmpty(error)) error = "Download failed. Check internet connection.";
            return false;
        }

        private static bool IsValidDownload(string path, int minBytes, byte[] magicPrefix)
        {
            try
            {
                FileInfo fi = new FileInfo(path);
                if (!fi.Exists || fi.Length < minBytes) return false;
                if (magicPrefix == null || magicPrefix.Length == 0) return true;
                byte[] header = new byte[magicPrefix.Length];
                using (FileStream fs = File.OpenRead(path))
                {
                    int read = fs.Read(header, 0, header.Length);
                    if (read < magicPrefix.Length) return false;
                }
                for (int i = 0; i < magicPrefix.Length; i++)
                {
                    if (header[i] != magicPrefix[i]) return false;
                }
                return true;
            }
            catch
            {
                return false;
            }
        }

        public static bool EnsureClientDll(out string error)
        {
            error = "";
            string dllPath = GetDllPath();
            try
            {
                if (File.Exists(dllPath) && new FileInfo(dllPath).Length > 10000) return true;
            }
            catch { }

            string cacheDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "LinkPad");
            if (!Directory.Exists(cacheDir)) Directory.CreateDirectory(cacheDir);
            string tempPkg = Path.Combine(cacheDir, "vigem_client.nupkg");

            if (!DownloadFileManaged(NugetUrl, tempPkg, 10000, new byte[] { 0x50, 0x4B }, null, out error))
            {
                return false;
            }

            try
            {
                string dir = Path.GetDirectoryName(dllPath);
                if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir)) Directory.CreateDirectory(dir);
                using (ZipArchive archive = ZipFile.OpenRead(tempPkg))
                {
                    ZipArchiveEntry entry = archive.GetEntry("lib/net452/Nefarius.ViGEm.Client.dll");
                    if (entry == null) entry = archive.GetEntry("lib/net461/Nefarius.ViGEm.Client.dll");
                    if (entry == null)
                    {
                        foreach (ZipArchiveEntry e in archive.Entries)
                        {
                            if (e.FullName.EndsWith("Nefarius.ViGEm.Client.dll", StringComparison.OrdinalIgnoreCase))
                            {
                                entry = e;
                                break;
                            }
                        }
                    }
                    if (entry == null)
                    {
                        error = "Nefarius.ViGEm.Client.dll not found in NuGet package.";
                        return false;
                    }
                    entry.ExtractToFile(dllPath, true);
                }
                try { File.Delete(tempPkg); } catch { }
                return File.Exists(dllPath);
            }
            catch (Exception ex)
            {
                error = ex.Message;
                return false;
            }
        }

        private static MethodInfo FindMethod(Type type, string name, Type[] paramTypes)
        {
            if (type == null) return null;
            BindingFlags flags = BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.Instance;
            Type cur = type;
            while (cur != null)
            {
                MethodInfo m = cur.GetMethod(name, flags, null, paramTypes, null);
                if (m != null) return m;
                cur = cur.BaseType;
            }
            Queue<Type> q = new Queue<Type>();
            HashSet<Type> visited = new HashSet<Type>();
            q.Enqueue(type);
            while (q.Count > 0)
            {
                Type t = q.Dequeue();
                foreach (Type iface in t.GetInterfaces())
                {
                    if (!visited.Add(iface)) continue;
                    MethodInfo m = iface.GetMethod(name, BindingFlags.Public | BindingFlags.Instance, null, paramTypes, null);
                    if (m != null) return m;
                    q.Enqueue(iface);
                }
            }
            return null;
        }

        private static object ResolveStaticMember(Type type, string name)
        {
            if (type == null) return null;
            FieldInfo f = type.GetField(name, BindingFlags.Public | BindingFlags.Static);
            if (f != null) return f.GetValue(null);
            PropertyInfo p = type.GetProperty(name, BindingFlags.Public | BindingFlags.Static);
            if (p != null) return p.GetValue(null, null);
            return null;
        }

        public static ViGEmXboxOutput TryCreate(bool uniqueSlotNames, out string failureReason)
        {
            failureReason = "";
            object client = null;
            object probePad = null;
            try
            {
                string dlErr;
                if (!EnsureClientDll(out dlErr))
                {
                    failureReason = "ViGEm client DLL missing: " + dlErr;
                    return null;
                }

                Assembly asm = Assembly.LoadFrom(GetDllPath());
                Type clientType = asm.GetType("Nefarius.ViGEm.Client.ViGEmClient");
                if (clientType == null)
                {
                    failureReason = "Invalid Nefarius.ViGEm.Client assembly.";
                    return null;
                }

                client = Activator.CreateInstance(clientType);
                MethodInfo createX360Default = clientType.GetMethod("CreateXbox360Controller", Type.EmptyTypes);
                MethodInfo createX360WithIds = clientType.GetMethod(
                    "CreateXbox360Controller",
                    new Type[] { typeof(ushort), typeof(ushort) });
                if (createX360Default == null && createX360WithIds == null)
                {
                    failureReason = "CreateXbox360Controller method not found.";
                    return null;
                }

                EnsureCustomOemNames(uniqueSlotNames);
                if (createX360WithIds != null)
                {
                    ushort vid = uniqueSlotNames ? LinkPadVendorId : CleanXboxVendorId;
                    ushort pid = uniqueSlotNames ? (ushort)(LinkPadProductBase + 1) : CleanXboxProductId;
                    probePad = createX360WithIds.Invoke(client, new object[] { vid, pid });
                }
                else
                {
                    probePad = createX360Default.Invoke(client, null);
                }

                if (probePad == null)
                {
                    failureReason = "Failed to instantiate Xbox 360 target.";
                    return null;
                }

                Type runtimePadType = probePad.GetType();
                Type padInterface = asm.GetType("Nefarius.ViGEm.Client.Targets.IXbox360Controller");
                Type btnType = asm.GetType("Nefarius.ViGEm.Client.Targets.Xbox360.Xbox360Button");
                Type axisType = asm.GetType("Nefarius.ViGEm.Client.Targets.Xbox360.Xbox360Axis");
                Type sliderType = asm.GetType("Nefarius.ViGEm.Client.Targets.Xbox360.Xbox360Slider");
                if (btnType == null)
                {
                    failureReason = "Xbox360Button type not found.";
                    return null;
                }

                // Bits 0..11 + 14..15 (L3/R3); bits 12..13 (L2/R2) use Xbox360Slider
                string[] btnNames = new string[]
                {
                    "Up", "Down", "Left", "Right",
                    "A", "B", "X", "Y",
                    "LeftShoulder", "RightShoulder", "Start", "Back",
                    null, null,
                    "LeftThumb", "RightThumb"
                };

                object[] btns = new object[16];
                for (int i = 0; i < btnNames.Length; i++)
                {
                    if (btnNames[i] == null) continue;
                    btns[i] = ResolveStaticMember(btnType, btnNames[i]);
                    if (btns[i] == null)
                    {
                        failureReason = "Missing Xbox360Button." + btnNames[i];
                        return null;
                    }
                }

                object[] axes = new object[4];
                if (axisType != null)
                {
                    axes[0] = ResolveStaticMember(axisType, "LeftThumbX");
                    axes[1] = ResolveStaticMember(axisType, "LeftThumbY");
                    axes[2] = ResolveStaticMember(axisType, "RightThumbX");
                    axes[3] = ResolveStaticMember(axisType, "RightThumbY");
                }

                object[] sliders = new object[2];
                if (sliderType != null)
                {
                    sliders[0] = ResolveStaticMember(sliderType, "LeftTrigger");
                    sliders[1] = ResolveStaticMember(sliderType, "RightTrigger");
                }

                MethodInfo setBtn = FindMethod(runtimePadType, "SetButtonState", new Type[] { btnType, typeof(bool) });
                if (setBtn == null && padInterface != null)
                {
                    setBtn = FindMethod(padInterface, "SetButtonState", new Type[] { btnType, typeof(bool) });
                }
                MethodInfo setAxis = axisType != null ? FindMethod(runtimePadType, "SetAxisValue", new Type[] { axisType, typeof(short) }) : null;
                if (setAxis == null && axisType != null && padInterface != null)
                {
                    setAxis = FindMethod(padInterface, "SetAxisValue", new Type[] { axisType, typeof(short) });
                }
                MethodInfo setSlider = sliderType != null ? FindMethod(runtimePadType, "SetSliderValue", new Type[] { sliderType, typeof(byte) }) : null;
                if (setSlider == null && sliderType != null && padInterface != null)
                {
                    setSlider = FindMethod(padInterface, "SetSliderValue", new Type[] { sliderType, typeof(byte) });
                }
                MethodInfo submit = FindMethod(runtimePadType, "SubmitReport", Type.EmptyTypes);
                if (submit == null && padInterface != null)
                {
                    submit = FindMethod(padInterface, "SubmitReport", Type.EmptyTypes);
                }
                MethodInfo connect = FindMethod(runtimePadType, "Connect", Type.EmptyTypes);
                if (connect == null && padInterface != null)
                {
                    connect = FindMethod(padInterface, "Connect", Type.EmptyTypes);
                }
                MethodInfo disconnect = FindMethod(runtimePadType, "Disconnect", Type.EmptyTypes);
                if (disconnect == null && padInterface != null)
                {
                    disconnect = FindMethod(padInterface, "Disconnect", Type.EmptyTypes);
                }

                if (setBtn == null || submit == null || connect == null || disconnect == null)
                {
                    failureReason = "ViGEm API mismatch (missing controller methods).";
                    return null;
                }

                PropertyInfo autoProp = runtimePadType.GetProperty("AutoSubmitReport");
                if (autoProp != null && autoProp.CanWrite)
                {
                    autoProp.SetValue(probePad, false, null);
                }

                // Plug Player 1 immediately so Windows joy.cpl & games see LinkPad right away.
                connect.Invoke(probePad, null);

                ViGEmXboxOutput instance = new ViGEmXboxOutput(
                    client, createX360Default, createX360WithIds,
                    setBtn, setAxis, setSlider, submit, connect, disconnect,
                    btns, axes, sliders, uniqueSlotNames);
                instance._pads[1] = probePad;
                return instance;
            }
            catch (TargetInvocationException tie)
            {
                failureReason = tie.InnerException != null ? tie.InnerException.Message : tie.Message;
                try { if (client is IDisposable) ((IDisposable)client).Dispose(); } catch { }
                return null;
            }
            catch (Exception ex)
            {
                failureReason = ex.Message;
                try { if (client is IDisposable) ((IDisposable)client).Dispose(); } catch { }
                return null;
            }
        }

        public void EnsureSlotConnected(int slot)
        {
            if (_pads.ContainsKey(slot)) return;
            object pad = CreateTargetForSlot(slot);
            PropertyInfo autoProp = pad.GetType().GetProperty("AutoSubmitReport");
            if (autoProp != null && autoProp.CanWrite) autoProp.SetValue(pad, false, null);
            _connectMethod.Invoke(pad, null);
            _pads[slot] = pad;
        }

        private static short ScaleAxis(byte rawSigned, bool invert)
        {
            int v = unchecked((sbyte)rawSigned);
            if (invert) v = -v;
            int scaled = v * 258;
            if (scaled > 32767) scaled = 32767;
            if (scaled < -32768) scaled = -32768;
            return (short)scaled;
        }

        public void ApplyState(
            int slot,
            int prevMask,
            int newMask,
            byte prevLx,
            byte prevLy,
            byte prevRx,
            byte prevRy,
            byte lx,
            byte ly,
            byte rx,
            byte ry)
        {
            object pad;
            if (!_pads.TryGetValue(slot, out pad))
            {
                EnsureSlotConnected(slot);
                pad = _pads[slot];
            }
            int changed = prevMask ^ newMask;
            bool axesChanged = prevLx != lx || prevLy != ly || prevRx != rx || prevRy != ry;
            if (changed == 0 && !axesChanged) return;

            for (int i = 0; i < 16; i++)
            {
                int bit = 1 << i;
                if ((changed & bit) == 0) continue;
                bool pressed = (newMask & bit) != 0;
                if (i == 12)
                {
                    if (_setSliderValue != null && _sliderObjects[0] != null)
                        _setSliderValue.Invoke(pad, new object[] { _sliderObjects[0], (byte)(pressed ? 255 : 0) });
                }
                else if (i == 13)
                {
                    if (_setSliderValue != null && _sliderObjects[1] != null)
                        _setSliderValue.Invoke(pad, new object[] { _sliderObjects[1], (byte)(pressed ? 255 : 0) });
                }
                else if (_buttonObjects[i] != null)
                {
                    _setButtonState.Invoke(pad, new object[] { _buttonObjects[i], pressed });
                }
            }

            if (_setAxisValue != null)
            {
                if (prevLx != lx && _axisObjects[0] != null)
                    _setAxisValue.Invoke(pad, new object[] { _axisObjects[0], ScaleAxis(lx, false) });
                if (prevLy != ly && _axisObjects[1] != null)
                    _setAxisValue.Invoke(pad, new object[] { _axisObjects[1], ScaleAxis(ly, true) });
                if (prevRx != rx && _axisObjects[2] != null)
                    _setAxisValue.Invoke(pad, new object[] { _axisObjects[2], ScaleAxis(rx, false) });
                if (prevRy != ry && _axisObjects[3] != null)
                    _setAxisValue.Invoke(pad, new object[] { _axisObjects[3], ScaleAxis(ry, true) });
            }

            _submitReport.Invoke(pad, null);
        }

        public void ReleaseSlot(int slot, int currentMask)
        {
            object pad;
            if (_pads.TryGetValue(slot, out pad))
            {
                try { ApplyState(slot, currentMask, 0, 1, 1, 1, 1, 0, 0, 0, 0); } catch { }
                if (slot != 1)
                {
                    try { _disconnectMethod.Invoke(pad, null); } catch { }
                    _pads.Remove(slot);
                }
            }
        }

        public void Dispose()
        {
            foreach (KeyValuePair<int, object> kv in new List<KeyValuePair<int, object>>(_pads))
            {
                try { _disconnectMethod.Invoke(kv.Value, null); } catch { }
            }
            _pads.Clear();
            IDisposable d = _client as IDisposable;
            if (d != null)
            {
                try { d.Dispose(); } catch { }
            }
        }
    }

    internal sealed class KeyboardSendInputOutput : IControllerOutput
    {
        [StructLayout(LayoutKind.Sequential)]
        private struct MOUSEINPUT
        {
            public int dx;
            public int dy;
            public uint mouseData;
            public uint dwFlags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct KEYBDINPUT
        {
            public ushort wVk;
            public ushort wScan;
            public uint dwFlags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct HARDWAREINPUT
        {
            public uint uMsg;
            public ushort wParamL;
            public ushort wParamH;
        }

        [StructLayout(LayoutKind.Explicit)]
        private struct INPUT_UNION
        {
            [FieldOffset(0)] public MOUSEINPUT mi;
            [FieldOffset(0)] public KEYBDINPUT ki;
            [FieldOffset(0)] public HARDWAREINPUT hi;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct INPUT
        {
            public uint type;
            public INPUT_UNION U;
        }

        [DllImport("user32.dll", SetLastError = true)]
        private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

        [DllImport("user32.dll")]
        private static extern uint MapVirtualKeyW(uint uCode, uint uMapType);

        private const uint INPUT_KEYBOARD = 1;
        private const uint KEYEVENTF_EXTENDEDKEY = 0x0001;
        private const uint KEYEVENTF_KEYUP = 0x0002;
        private const uint KEYEVENTF_SCANCODE = 0x0008;

        private static readonly Dictionary<int, ushort[]> SlotKeyMaps = new Dictionary<int, ushort[]>
        {
            // P1: W S A D + K J I U + Q E + Enter Space + Z C LShift F
            { 1, new ushort[] { 0x57, 0x53, 0x41, 0x44, 0x4B, 0x4A, 0x49, 0x55, 0x51, 0x45, 0x0D, 0x20, 0x5A, 0x43, 0xA0, 0x46 } },
            // P2: Arrows + . , / ; + [ ] + RShift \ + - = ' P
            { 2, new ushort[] { 0x26, 0x28, 0x25, 0x27, 0xBE, 0xBC, 0xBF, 0xBA, 0xDB, 0xDD, 0xA1, 0xDC, 0xBD, 0xBB, 0xDE, 0x50 } },
            // P3: I K J L + N B M H + Y O + 9 0 + 7 8 T G
            { 3, new ushort[] { 0x49, 0x4B, 0x4A, 0x4C, 0x4E, 0x42, 0x4D, 0x48, 0x59, 0x4F, 0x39, 0x30, 0x37, 0x38, 0x54, 0x47 } },
            // P4: Numpad 8 2 4 6 + 1 0 3 5 + 7 9 + Enter . + / * - +
            { 4, new ushort[] { 0x68, 0x62, 0x64, 0x66, 0x61, 0x60, 0x63, 0x65, 0x67, 0x69, 0x0D, 0x6E, 0x6F, 0x6A, 0x6D, 0x6B } }
        };

        public string DisplayName { get { return "Keyboard Mode (WASD / Arrows / Numpad)"; } }
        public bool IsXboxActive { get { return false; } }

        public void EnsureSlotConnected(int slot) { }

        public void ApplyState(
            int slot,
            int prevMask,
            int newMask,
            byte prevLx,
            byte prevLy,
            byte prevRx,
            byte prevRy,
            byte lx,
            byte ly,
            byte rx,
            byte ry)
        {
            ushort[] map;
            if (!SlotKeyMaps.TryGetValue(slot, out map)) return;
            int changed = prevMask ^ newMask;
            if (changed == 0) return;

            List<INPUT> inputs = new List<INPUT>();
            for (int i = 0; i < 16; i++)
            {
                int bit = 1 << i;
                if ((changed & bit) != 0)
                {
                    ushort vk = map[i];
                    ushort scan = (ushort)(MapVirtualKeyW(vk, 0) & 0xFF);
                    uint flags = KEYEVENTF_SCANCODE;
                    if (vk == 0x25 || vk == 0x26 || vk == 0x27 || vk == 0x28 || vk == 0xA1)
                    {
                        flags |= KEYEVENTF_EXTENDEDKEY;
                    }
                    if ((newMask & bit) == 0) flags |= KEYEVENTF_KEYUP;

                    INPUT inp = new INPUT();
                    inp.type = INPUT_KEYBOARD;
                    inp.U.ki.wVk = 0;
                    inp.U.ki.wScan = scan;
                    inp.U.ki.dwFlags = flags;
                    inputs.Add(inp);
                }
            }
            if (inputs.Count > 0)
            {
                SendInput((uint)inputs.Count, inputs.ToArray(), Marshal.SizeOf(typeof(INPUT)));
            }
        }

        public void ReleaseSlot(int slot, int currentMask)
        {
            if (currentMask != 0) ApplyState(slot, currentMask, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public void Dispose() { }
    }

    internal sealed class MonitorOnlyOutput : IControllerOutput
    {
        public string DisplayName { get { return "Monitor Only (Install Xbox 360 Driver)"; } }
        public bool IsXboxActive { get { return false; } }
        public void EnsureSlotConnected(int slot) { }
        public void ApplyState(int slot, int prevMask, int newMask, byte prevLx, byte prevLy, byte prevRx, byte prevRy, byte lx, byte ly, byte rx, byte ry) { }
        public void ReleaseSlot(int slot, int currentMask) { }
        public void Dispose() { }
    }

    internal sealed class PlayerSlotState
    {
        public int Slot;
        public bool Connected;
        public IPEndPoint EndPoint;
        public string DeviceName = "";
        public int ClientId;
        public int ButtonMask;
        public byte Layout;
        public byte Lx;
        public byte Ly;
        public byte Rx;
        public byte Ry;
        public int RttMs;
        public long LastSeenTicks;
        public bool IsTrusted;
    }

    internal sealed class HostEngine : IDisposable
    {
        private readonly object _lock = new object();
        private readonly PlayerSlotState[] _slots = new PlayerSlotState[Protocol.MAX_PLAYERS];
        private readonly HashSet<int> _trustedIds = new HashSet<int>();
        private readonly string _trustedFilePath;

        private UdpClient _udp;
        private UdpClient _beaconUdp;
        private volatile bool _running;
        private int _pairingCodeInt;
        private string _pairingCode;
        private int _pktCounter;
        private int _pps;
        private IControllerOutput _output;

        public bool RememberTrustedDevices = true;
        public bool AllowAnyFavoriteWithoutCode = false;
        public bool UniqueSlotNames = true;
        public string LastStatusError = "";

        public event Action StateChanged;

        public HostEngine()
        {
            for (int i = 0; i < Protocol.MAX_PLAYERS; i++)
            {
                _slots[i] = new PlayerSlotState { Slot = i + 1 };
            }
            string dir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "LinkPad");
            if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);
            _trustedFilePath = Path.Combine(dir, "trusted_devices.txt");
            LoadTrustedDevices();

            Random rng = new Random();
            _pairingCodeInt = rng.Next(100000, 999999);
            _pairingCode = _pairingCodeInt.ToString();

            InitBestOutput(false);
        }

        public string HostName
        {
            get
            {
                string raw = "PC \u00B7 " + Environment.MachineName;
                return raw.Length > Protocol.MAX_NAME_BYTES ? raw.Substring(0, Protocol.MAX_NAME_BYTES) : raw;
            }
        }

        public string PairingCode { get { lock (_lock) return _pairingCode; } }
        public string OutputModeName { get { lock (_lock) return _output.DisplayName; } }
        public bool IsXboxActive { get { lock (_lock) return _output.IsXboxActive; } }
        public int PacketsPerSecond { get { lock (_lock) return _pps; } }
        public int TrustedCount { get { lock (_lock) return _trustedIds.Count; } }

        public bool InitBestOutput(bool forceKeyboard)
        {
            lock (_lock)
            {
                if (_output != null)
                {
                    for (int i = 0; i < _slots.Length; i++)
                    {
                        if (_slots[i].Connected)
                        {
                            _output.ReleaseSlot(_slots[i].Slot, _slots[i].ButtonMask);
                            _slots[i].ButtonMask = 0;
                            _slots[i].Lx = 0;
                            _slots[i].Ly = 0;
                            _slots[i].Rx = 0;
                            _slots[i].Ry = 0;
                        }
                    }
                    _output.Dispose();
                }

                if (forceKeyboard)
                {
                    _output = new KeyboardSendInputOutput();
                    LastStatusError = "";
                }
                else
                {
                    string reason;
                    ViGEmXboxOutput xbox = ViGEmXboxOutput.TryCreate(UniqueSlotNames, out reason);
                    if (xbox != null)
                    {
                        _output = xbox;
                        LastStatusError = "";
                        for (int i = 0; i < _slots.Length; i++)
                        {
                            if (_slots[i].Connected)
                            {
                                try { _output.EnsureSlotConnected(_slots[i].Slot); } catch { }
                            }
                        }
                    }
                    else
                    {
                        _output = new MonitorOnlyOutput();
                        LastStatusError = ViGEmXboxOutput.IsDriverInstalled()
                            ? reason
                            : "ViGEmBus Xbox 360 driver not installed.";
                    }
                }
            }
            Notify();
            return _output.IsXboxActive;
        }

        public void Start()
        {
            if (_running) return;
            _running = true;

            _udp = new UdpClient(AddressFamily.InterNetwork);
            _udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
            try { _udp.Client.SetSocketOption(SocketOptionLevel.IP, SocketOptionName.TypeOfService, 0x10); } catch { }
            _udp.Client.Bind(new IPEndPoint(IPAddress.Any, Protocol.HOST_UDP_PORT));

            _beaconUdp = new UdpClient(AddressFamily.InterNetwork);
            _beaconUdp.EnableBroadcast = true;
            try { _beaconUdp.Client.SetSocketOption(SocketOptionLevel.IP, SocketOptionName.MulticastTimeToLive, 2); } catch { }

            new Thread(RecvLoop) { IsBackground = true }.Start();
            new Thread(BeaconLoop) { IsBackground = true }.Start();
            new Thread(WatchdogLoop) { IsBackground = true }.Start();
            Notify();
        }

        public void RegenerateCode()
        {
            lock (_lock)
            {
                Random rng = new Random();
                _pairingCodeInt = rng.Next(100000, 999999);
                _pairingCode = _pairingCodeInt.ToString();
            }
            Notify();
        }

        public void KickPlayer(int slot)
        {
            IPEndPoint ep = null;
            lock (_lock)
            {
                int idx = slot - 1;
                if (idx >= 0 && idx < _slots.Length && _slots[idx].Connected)
                {
                    ep = _slots[idx].EndPoint;
                    _output.ReleaseSlot(slot, _slots[idx].ButtonMask);
                    _slots[idx] = new PlayerSlotState { Slot = slot };
                }
            }
            if (ep != null && _udp != null)
            {
                try
                {
                    byte[] bye = Protocol.Encode(new Protocol.Packet { Type = Protocol.PKT_BYE, Slot = slot });
                    _udp.Send(bye, bye.Length, ep);
                }
                catch { }
            }
            Notify();
        }

        public void TrustConnectedSlot(int slot)
        {
            lock (_lock)
            {
                int idx = slot - 1;
                if (idx >= 0 && idx < _slots.Length && _slots[idx].Connected)
                {
                    int cid = _slots[idx].ClientId;
                    if (cid != 0)
                    {
                        _trustedIds.Add(cid);
                        _slots[idx].IsTrusted = true;
                        SaveTrustedDevices();
                    }
                }
            }
            Notify();
        }

        public void ClearTrustedDevices()
        {
            lock (_lock)
            {
                _trustedIds.Clear();
                for (int i = 0; i < _slots.Length; i++) _slots[i].IsTrusted = false;
                SaveTrustedDevices();
            }
            Notify();
        }

        public PlayerSlotState[] GetSlotsSnapshot()
        {
            lock (_lock)
            {
                PlayerSlotState[] copy = new PlayerSlotState[_slots.Length];
                for (int i = 0; i < _slots.Length; i++)
                {
                    PlayerSlotState s = _slots[i];
                    copy[i] = new PlayerSlotState
                    {
                        Slot = s.Slot,
                        Connected = s.Connected,
                        EndPoint = s.EndPoint,
                        DeviceName = s.DeviceName,
                        ClientId = s.ClientId,
                        ButtonMask = s.ButtonMask,
                        Layout = s.Layout,
                        Lx = s.Lx,
                        Ly = s.Ly,
                        Rx = s.Rx,
                        Ry = s.Ry,
                        RttMs = s.RttMs,
                        LastSeenTicks = s.LastSeenTicks,
                        IsTrusted = s.IsTrusted
                    };
                }
                return copy;
            }
        }

        public static string GetPrimaryLocalIpv4()
        {
            try
            {
                using (Socket probe = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp))
                {
                    probe.Connect("8.8.8.8", 80);
                    IPEndPoint ep = probe.LocalEndPoint as IPEndPoint;
                    if (ep != null && !IPAddress.IsLoopback(ep.Address)) return ep.Address.ToString();
                }
            }
            catch { }

            try
            {
                foreach (NetworkInterface ni in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (ni.OperationalStatus != OperationalStatus.Up) continue;
                    if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                    foreach (UnicastIPAddressInformation ua in ni.GetIPProperties().UnicastAddresses)
                    {
                        if (ua.Address.AddressFamily == AddressFamily.InterNetwork)
                        {
                            return ua.Address.ToString();
                        }
                    }
                }
            }
            catch { }
            return "127.0.0.1";
        }

        private List<IPEndPoint> GetBeaconTargets()
        {
            List<IPEndPoint> list = new List<IPEndPoint>();
            HashSet<string> seen = new HashSet<string>();
            Action<IPAddress> add = delegate (IPAddress addr)
            {
                string s = addr.ToString();
                if (seen.Add(s)) list.Add(new IPEndPoint(addr, Protocol.BEACON_UDP_PORT));
            };

            add(IPAddress.Broadcast);
            add(IPAddress.Parse(Protocol.MULTICAST_GROUP));
            try
            {
                foreach (NetworkInterface ni in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (ni.OperationalStatus != OperationalStatus.Up) continue;
                    if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                    foreach (UnicastIPAddressInformation ua in ni.GetIPProperties().UnicastAddresses)
                    {
                        if (ua.Address.AddressFamily == AddressFamily.InterNetwork && ua.IPv4Mask != null)
                        {
                            byte[] ip = ua.Address.GetAddressBytes();
                            byte[] mask = ua.IPv4Mask.GetAddressBytes();
                            byte[] bcast = new byte[4];
                            for (int i = 0; i < 4; i++) bcast[i] = (byte)(ip[i] | ~mask[i]);
                            add(new IPAddress(bcast));
                        }
                    }
                }
            }
            catch { }
            return list;
        }

        private void RecvLoop()
        {
            while (_running && _udp != null)
            {
                try
                {
                    IPEndPoint remote = new IPEndPoint(IPAddress.Any, 0);
                    byte[] data = _udp.Receive(ref remote);
                    Protocol.Packet pkt = Protocol.Decode(data, data.Length);
                    if (pkt != null) HandlePacket(pkt, remote);
                }
                catch (SocketException)
                {
                    if (!_running) break;
                }
                catch (ObjectDisposedException)
                {
                    break;
                }
                catch { }
            }
        }

        private void HandlePacket(Protocol.Packet pkt, IPEndPoint remote)
        {
            Interlocked.Increment(ref _pktCounter);
            long now = Environment.TickCount;

            if (pkt.Type == Protocol.PKT_HELLO)
            {
                int clientId = pkt.ExtractClientId32();
                int assignedSlot = -1;
                byte rejectCode = 0;
                int codeInt;

                lock (_lock)
                {
                    codeInt = _pairingCodeInt;
                    bool trustedBypass = pkt.ExtraByte == Protocol.HELLO_MODE_TRUSTED_FAVORITE &&
                        (AllowAnyFavoriteWithoutCode || (clientId != 0 && _trustedIds.Contains(clientId)));

                    if (pkt.ExtraByte == Protocol.HELLO_MODE_TRUSTED_FAVORITE && !trustedBypass)
                    {
                        rejectCode = Protocol.REJECT_UNTRUSTED;
                    }
                    else if (pkt.ExtraByte != Protocol.HELLO_MODE_TRUSTED_FAVORITE && pkt.IntPayload != _pairingCodeInt)
                    {
                        rejectCode = Protocol.REJECT_INVALID_CODE;
                    }
                    else
                    {
                        if (RememberTrustedDevices && clientId != 0 && pkt.ExtraByte != Protocol.HELLO_MODE_TRUSTED_FAVORITE)
                        {
                            if (_trustedIds.Add(clientId)) SaveTrustedDevices();
                        }

                        int existingIdx = -1;
                        for (int i = 0; i < _slots.Length; i++)
                        {
                            PlayerSlotState s = _slots[i];
                            if (s.Connected && (s.EndPoint.Equals(remote)
                                || (clientId != 0 && s.ClientId == clientId)
                                || (s.EndPoint.Address.Equals(remote.Address) && !string.IsNullOrEmpty(pkt.DeviceName) && s.DeviceName == pkt.DeviceName)))
                            {
                                existingIdx = i;
                                break;
                            }
                        }

                        if (existingIdx >= 0)
                        {
                            PlayerSlotState s = _slots[existingIdx];
                            s.EndPoint = remote;
                            if (!string.IsNullOrEmpty(pkt.DeviceName)) s.DeviceName = pkt.DeviceName;
                            if (clientId != 0) s.ClientId = clientId;
                            s.IsTrusted = clientId != 0 && _trustedIds.Contains(clientId);
                            s.LastSeenTicks = now;
                            assignedSlot = s.Slot;
                            try { _output.EnsureSlotConnected(assignedSlot); } catch { }
                        }
                        else
                        {
                            int freeIdx = -1;
                            for (int i = 0; i < _slots.Length; i++)
                            {
                                if (!_slots[i].Connected) { freeIdx = i; break; }
                            }
                            if (freeIdx < 0)
                            {
                                rejectCode = Protocol.REJECT_HOST_FULL;
                            }
                            else
                            {
                                assignedSlot = freeIdx + 1;
                                _slots[freeIdx] = new PlayerSlotState
                                {
                                    Slot = assignedSlot,
                                    Connected = true,
                                    EndPoint = remote,
                                    DeviceName = string.IsNullOrEmpty(pkt.DeviceName) ? ("Player " + assignedSlot) : pkt.DeviceName,
                                    ClientId = clientId,
                                    IsTrusted = clientId != 0 && _trustedIds.Contains(clientId),
                                    LastSeenTicks = now
                                };
                                try { _output.EnsureSlotConnected(assignedSlot); } catch { }
                            }
                        }
                    }
                }

                if (rejectCode != 0)
                {
                    SendReply(new Protocol.Packet { Type = Protocol.PKT_REJECT, ExtraByte = rejectCode }, remote);
                }
                else
                {
                    SendReply(new Protocol.Packet { Type = Protocol.PKT_WELCOME, Slot = assignedSlot, IntPayload = codeInt }, remote);
                    Notify();
                }
            }
            else if (pkt.Type == Protocol.PKT_INPUT)
            {
                bool changed = false;
                lock (_lock)
                {
                    PlayerSlotState s = FindSlot(pkt.Slot, remote);
                    if (s != null)
                    {
                        int prevMask = s.ButtonMask;
                        byte prevLx = s.Lx;
                        byte prevLy = s.Ly;
                        byte prevRx = s.Rx;
                        byte prevRy = s.Ry;
                        byte prevLayout = s.Layout;

                        s.ButtonMask = pkt.ButtonMask;
                        s.Layout = pkt.Layout;
                        s.Lx = pkt.Lx;
                        s.Ly = pkt.Ly;
                        s.Rx = pkt.Rx;
                        s.Ry = pkt.Ry;
                        s.LastSeenTicks = now;

                        if (prevMask != pkt.ButtonMask
                            || prevLx != pkt.Lx
                            || prevLy != pkt.Ly
                            || prevRx != pkt.Rx
                            || prevRy != pkt.Ry
                            || prevLayout != pkt.Layout)
                        {
                            try
                            {
                                _output.ApplyState(
                                    s.Slot,
                                    prevMask,
                                    pkt.ButtonMask,
                                    prevLx,
                                    prevLy,
                                    prevRx,
                                    prevRy,
                                    pkt.Lx,
                                    pkt.Ly,
                                    pkt.Rx,
                                    pkt.Ry);
                            }
                            catch { }
                            changed = true;
                        }
                    }
                }
                if (changed) Notify();
            }
            else if (pkt.Type == Protocol.PKT_PING)
            {
                bool rttChanged = false;
                lock (_lock)
                {
                    PlayerSlotState s = FindSlot(pkt.Slot, remote);
                    if (s != null)
                    {
                        s.LastSeenTicks = now;
                        int rtt = pkt.ExtraByte & 0xFF;
                        if (rtt > 0 && rtt != s.RttMs)
                        {
                            s.RttMs = rtt;
                            rttChanged = true;
                        }
                    }
                }
                SendReply(new Protocol.Packet { Type = Protocol.PKT_PONG, Slot = pkt.Slot, IntPayload = pkt.IntPayload }, remote);
                if (rttChanged) Notify();
            }
            else if (pkt.Type == Protocol.PKT_BYE)
            {
                bool removed = false;
                lock (_lock)
                {
                    PlayerSlotState s = FindSlot(pkt.Slot, remote);
                    if (s != null)
                    {
                        _output.ReleaseSlot(s.Slot, s.ButtonMask);
                        _slots[s.Slot - 1] = new PlayerSlotState { Slot = s.Slot };
                        removed = true;
                    }
                }
                if (removed) Notify();
            }
        }

        private PlayerSlotState FindSlot(int hintedSlot, IPEndPoint remote)
        {
            if (hintedSlot >= 1 && hintedSlot <= Protocol.MAX_PLAYERS)
            {
                PlayerSlotState s = _slots[hintedSlot - 1];
                if (s.Connected && s.EndPoint.Equals(remote)) return s;
            }
            for (int i = 0; i < _slots.Length; i++)
            {
                if (_slots[i].Connected && _slots[i].EndPoint.Equals(remote)) return _slots[i];
            }
            return null;
        }

        private void SendReply(Protocol.Packet pkt, IPEndPoint remote)
        {
            if (_udp == null) return;
            try
            {
                byte[] bytes = Protocol.Encode(pkt);
                _udp.Send(bytes, bytes.Length, remote);
            }
            catch { }
        }

        private void BeaconLoop()
        {
            while (_running && _beaconUdp != null)
            {
                int active = 0;
                lock (_lock)
                {
                    for (int i = 0; i < _slots.Length; i++) if (_slots[i].Connected) active++;
                }
                byte[] beacon = Protocol.Encode(new Protocol.Packet
                {
                    Type = Protocol.PKT_BEACON,
                    ExtraByte = (byte)active,
                    IntPayload = Protocol.HOST_UDP_PORT,
                    DeviceName = HostName
                });

                foreach (IPEndPoint ep in GetBeaconTargets())
                {
                    try { _beaconUdp.Send(beacon, beacon.Length, ep); } catch { }
                }
                Thread.Sleep(Protocol.BEACON_INTERVAL_MS);
            }
        }

        private void WatchdogLoop()
        {
            while (_running)
            {
                Thread.Sleep(1000);
                long now = Environment.TickCount;
                bool dirty = false;
                lock (_lock)
                {
                    _pps = Interlocked.Exchange(ref _pktCounter, 0);
                    for (int i = 0; i < _slots.Length; i++)
                    {
                        PlayerSlotState s = _slots[i];
                        if (s.Connected && unchecked((int)(now - s.LastSeenTicks)) > Protocol.CLIENT_TIMEOUT_MS)
                        {
                            _output.ReleaseSlot(s.Slot, s.ButtonMask);
                            _slots[i] = new PlayerSlotState { Slot = s.Slot };
                            dirty = true;
                        }
                    }
                }
                if (dirty || _pps > 0) Notify();
            }
        }

        private void LoadTrustedDevices()
        {
            try
            {
                if (!File.Exists(_trustedFilePath)) return;
                foreach (string line in File.ReadAllLines(_trustedFilePath))
                {
                    int id;
                    if (int.TryParse(line.Trim(), out id) && id != 0) _trustedIds.Add(id);
                }
            }
            catch { }
        }

        private void SaveTrustedDevices()
        {
            try
            {
                List<string> lines = new List<string>();
                foreach (int id in _trustedIds) lines.Add(id.ToString());
                File.WriteAllLines(_trustedFilePath, lines.ToArray());
            }
            catch { }
        }

        private void Notify()
        {
            Action handler = StateChanged;
            if (handler != null) handler();
        }

        public void Dispose()
        {
            _running = false;
            lock (_lock)
            {
                for (int i = 0; i < _slots.Length; i++)
                {
                    if (_slots[i].Connected)
                    {
                        SendReply(new Protocol.Packet { Type = Protocol.PKT_BYE, Slot = _slots[i].Slot }, _slots[i].EndPoint);
                        _output.ReleaseSlot(_slots[i].Slot, _slots[i].ButtonMask);
                        _slots[i] = new PlayerSlotState { Slot = _slots[i].Slot };
                    }
                }
                if (_output != null) _output.Dispose();
            }
            try { if (_udp != null) _udp.Close(); } catch { }
            try { if (_beaconUdp != null) _beaconUdp.Close(); } catch { }
        }
    }

    internal static class BrandGlyph
    {
        public static void DrawControllerLogo(Graphics g, RectangleF bounds, Color bodyColor, Color cutoutColor)
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            float aspect = 1.48f;
            float w = bounds.Width;
            float h = w / aspect;
            if (h > bounds.Height)
            {
                h = bounds.Height;
                w = h * aspect;
            }
            float ox = bounds.X + (bounds.Width - w) * 0.5f;
            float oy = bounds.Y + (bounds.Height - h) * 0.5f;

            Func<float, float, PointF> pt = delegate (float nx, float ny)
            {
                return new PointF(ox + nx * w, oy + ny * h);
            };

            using (GraphicsPath body = new GraphicsPath())
            {
                // Exact mirror-symmetric sculpted controller matching LinkPad Android app icon
                body.StartFigure();
                body.AddBezier(pt(0.50f, 0.20f), pt(0.58f, 0.20f), pt(0.65f, 0.12f), pt(0.74f, 0.12f));
                body.AddBezier(pt(0.74f, 0.12f), pt(0.88f, 0.12f), pt(0.97f, 0.36f), pt(0.97f, 0.68f));
                body.AddBezier(pt(0.97f, 0.68f), pt(0.97f, 0.82f), pt(0.94f, 0.90f), pt(0.88f, 0.90f));
                body.AddBezier(pt(0.88f, 0.90f), pt(0.80f, 0.90f), pt(0.74f, 0.68f), pt(0.68f, 0.68f));
                body.AddBezier(pt(0.68f, 0.68f), pt(0.58f, 0.67f), pt(0.42f, 0.67f), pt(0.32f, 0.68f));
                body.AddBezier(pt(0.32f, 0.68f), pt(0.26f, 0.68f), pt(0.20f, 0.90f), pt(0.12f, 0.90f));
                body.AddBezier(pt(0.12f, 0.90f), pt(0.06f, 0.90f), pt(0.03f, 0.82f), pt(0.03f, 0.68f));
                body.AddBezier(pt(0.03f, 0.68f), pt(0.03f, 0.36f), pt(0.12f, 0.12f), pt(0.26f, 0.12f));
                body.AddBezier(pt(0.26f, 0.12f), pt(0.35f, 0.12f), pt(0.42f, 0.20f), pt(0.50f, 0.20f));
                body.CloseFigure();

                using (SolidBrush b = new SolidBrush(bodyColor))
                {
                    g.FillPath(b, body);
                }
            }

            // Left D-pad cross cutout
            float dcx = ox + 0.265f * w;
            float dcy = oy + 0.45f * h;
            float armW = Math.Max(2.2f, w * 0.072f);
            float armL = Math.Max(6.0f, w * 0.21f);
            using (SolidBrush cb = new SolidBrush(cutoutColor))
            {
                RectangleF hBar = new RectangleF(dcx - armL * 0.5f, dcy - armW * 0.5f, armL, armW);
                RectangleF vBar = new RectangleF(dcx - armW * 0.5f, dcy - armL * 0.5f, armW, armL);
                using (GraphicsPath hp = MainForm.CreateRoundedRectF(hBar, armW * 0.24f))
                using (GraphicsPath vp = MainForm.CreateRoundedRectF(vBar, armW * 0.24f))
                {
                    g.FillPath(cb, hp);
                    g.FillPath(cb, vp);
                }

                // Right 4 ABXY diamond dots cutout (guaranteed spacing at all icon sizes)
                float bx = ox + 0.735f * w;
                float by = oy + 0.45f * h;
                float r = Math.Max(1.35f, w * 0.044f);
                float spread = Math.Max(3.4f, r * 2.35f);

                g.FillEllipse(cb, bx - r, by - spread - r, r * 2f, r * 2f); // Y (top)
                g.FillEllipse(cb, bx - r, by + spread - r, r * 2f, r * 2f); // A (bottom)
                g.FillEllipse(cb, bx - spread - r, by - r, r * 2f, r * 2f); // X (left)
                g.FillEllipse(cb, bx + spread - r, by - r, r * 2f, r * 2f); // B (right)
            }
        }
    }

    internal sealed class GlassCardPanel : Panel
    {
        public int CornerRadius = 18;
        public Color TopFill = Color.FromArgb(24, 31, 45);
        public Color BottomFill = Color.FromArgb(15, 20, 30);
        public Color RimTop = Color.FromArgb(62, 255, 255, 255);
        public Color RimBottom = Color.FromArgb(16, 255, 255, 255);
        public Color AccentBorder = Color.Empty;

        public GlassCardPanel()
        {
            DoubleBuffered = true;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.SupportsTransparentBackColor, true);
            BackColor = Color.Transparent;
        }

        protected override void OnPaintBackground(PaintEventArgs e)
        {
            base.OnPaintBackground(e);
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            Rectangle rect = new Rectangle(0, 0, Width - 1, Height - 1);
            if (rect.Width <= 2 || rect.Height <= 2) return;

            using (GraphicsPath path = MainForm.CreateRoundedRect(rect, CornerRadius))
            {
                using (LinearGradientBrush fill = new LinearGradientBrush(rect, TopFill, BottomFill, 90f))
                {
                    g.FillPath(fill, path);
                }

                Rectangle sheen = new Rectangle(rect.X + 1, rect.Y + 1, rect.Width - 2, Math.Max(8, rect.Height / 3));
                using (LinearGradientBrush sheenBrush = new LinearGradientBrush(
                    sheen, Color.FromArgb(16, 255, 255, 255), Color.FromArgb(0, 255, 255, 255), 90f))
                {
                    g.SetClip(path);
                    g.FillRectangle(sheenBrush, sheen);
                    g.ResetClip();
                }
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            Rectangle rect = new Rectangle(0, 0, Width - 1, Height - 1);
            if (rect.Width <= 2 || rect.Height <= 2) return;

            using (GraphicsPath path = MainForm.CreateRoundedRect(rect, CornerRadius))
            {
                if (!AccentBorder.IsEmpty)
                {
                    using (Pen ap = new Pen(Color.FromArgb(155, AccentBorder), 1.25f))
                    {
                        g.DrawPath(ap, path);
                    }
                }
                else
                {
                    using (LinearGradientBrush rimBrush = new LinearGradientBrush(rect, RimTop, RimBottom, 60f))
                    using (Pen pen = new Pen(rimBrush, 1.0f))
                    {
                        g.DrawPath(pen, path);
                    }
                }
            }
        }
    }

    internal sealed class GlassLogoBadge : Control
    {
        public Color BodyColor = Color.FromArgb(236, 242, 250);
        public Color CutoutColor = Color.FromArgb(15, 20, 29);

        public GlassLogoBadge()
        {
            DoubleBuffered = true;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.SupportsTransparentBackColor, true);
            BackColor = Color.Transparent;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            Rectangle rect = new Rectangle(0, 0, Width - 1, Height - 1);
            using (GraphicsPath path = MainForm.CreateRoundedRect(rect, 11))
            {
                using (LinearGradientBrush bg = new LinearGradientBrush(
                    rect, Color.FromArgb(34, 45, 64), Color.FromArgb(17, 23, 34), 90f))
                {
                    g.FillPath(bg, path);
                }
                using (Pen rim = new Pen(Color.FromArgb(72, 255, 255, 255), 1.0f))
                {
                    g.DrawPath(rim, path);
                }
            }
            BrandGlyph.DrawControllerLogo(
                g,
                new RectangleF(6f, 6f, Width - 12f, Height - 12f),
                BodyColor,
                CutoutColor);
        }
    }

    internal sealed class GlassPill : Control
    {
        public int CornerRadius = 9;
        public Color FillColor = Color.FromArgb(15, 20, 29);
        public Color BorderColor = Color.FromArgb(44, 56, 76);
        public Color DotColor = Color.Empty;
        public Color TextColor = Color.FromArgb(214, 223, 237);

        private bool _hovered;

        public GlassPill()
        {
            DoubleBuffered = true;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.SupportsTransparentBackColor, true);
            BackColor = Color.Transparent;
        }

        protected override void OnMouseEnter(EventArgs e) { _hovered = true; Invalidate(); base.OnMouseEnter(e); }
        protected override void OnMouseLeave(EventArgs e) { _hovered = false; Invalidate(); base.OnMouseLeave(e); }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            Rectangle rect = new Rectangle(0, 0, Width - 1, Height - 1);
            if (rect.Width <= 2 || rect.Height <= 2) return;

            Color bg = (_hovered && Cursor == Cursors.Hand)
                ? Color.FromArgb(Math.Min(255, FillColor.R + 10), Math.Min(255, FillColor.G + 12), Math.Min(255, FillColor.B + 16))
                : FillColor;

            using (GraphicsPath path = MainForm.CreateRoundedRect(rect, CornerRadius))
            {
                using (SolidBrush sb = new SolidBrush(bg)) g.FillPath(sb, path);
                using (Pen pen = new Pen(BorderColor, 1.0f)) g.DrawPath(pen, path);
            }

            Rectangle textRect = new Rectangle(4, 0, Width - 8, Height);
            if (!DotColor.IsEmpty)
            {
                int dotSize = 6;
                int dy = (Height - dotSize) / 2;
                using (SolidBrush db = new SolidBrush(DotColor))
                {
                    g.FillEllipse(db, 9, dy, dotSize, dotSize);
                }
                textRect = new Rectangle(18, 0, Width - 22, Height);
            }

            TextRenderer.DrawText(
                g,
                Text ?? "",
                Font,
                textRect,
                TextColor,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis | TextFormatFlags.NoPrefix);
        }
    }

    internal sealed class GlassButton : Control
    {
        public int CornerRadius = 10;
        public Color BgColor = Color.FromArgb(27, 34, 48);
        public Color TextColor = Color.FromArgb(243, 246, 251);
        public Color BorderColor = Color.FromArgb(62, 77, 102);
        public Color DisabledBgColor = Color.FromArgb(20, 48, 37);
        public Color DisabledTextColor = Color.FromArgb(92, 224, 168);
        public Color DisabledBorderColor = Color.FromArgb(52, 138, 102);

        private bool _hovered;
        private bool _pressed;

        public GlassButton()
        {
            DoubleBuffered = true;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.SupportsTransparentBackColor | ControlStyles.Selectable, true);
            BackColor = Color.Transparent;
            Cursor = Cursors.Hand;
            TabStop = true;
        }

        protected override void OnEnabledChanged(EventArgs e)
        {
            Cursor = Enabled ? Cursors.Hand : Cursors.Default;
            Invalidate();
            base.OnEnabledChanged(e);
        }

        protected override void OnMouseEnter(EventArgs e) { _hovered = true; Invalidate(); base.OnMouseEnter(e); }
        protected override void OnMouseLeave(EventArgs e) { _hovered = false; _pressed = false; Invalidate(); base.OnMouseLeave(e); }
        protected override void OnMouseDown(MouseEventArgs e) { if (e.Button == MouseButtons.Left) { _pressed = true; Invalidate(); } base.OnMouseDown(e); }
        protected override void OnMouseUp(MouseEventArgs e) { _pressed = false; Invalidate(); base.OnMouseUp(e); }
        protected override void OnKeyDown(KeyEventArgs e)
        {
            if (Enabled && (e.KeyCode == Keys.Space || e.KeyCode == Keys.Enter))
            {
                OnClick(EventArgs.Empty);
                e.Handled = true;
            }
            base.OnKeyDown(e);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            Rectangle rect = new Rectangle(0, 0, Width - 1, Height - 1);
            if (rect.Width <= 2 || rect.Height <= 2) return;

            Color bg = Enabled ? BgColor : DisabledBgColor;
            Color fg = Enabled ? TextColor : DisabledTextColor;
            Color border = Enabled ? BorderColor : DisabledBorderColor;

            if (Enabled && _pressed)
            {
                bg = Color.FromArgb(Math.Max(0, bg.R - 10), Math.Max(0, bg.G - 10), Math.Max(0, bg.B - 10));
            }
            else if (Enabled && _hovered)
            {
                bg = Color.FromArgb(Math.Min(255, bg.R + 14), Math.Min(255, bg.G + 16), Math.Min(255, bg.B + 22));
                border = Color.FromArgb(Math.Min(255, border.R + 28), Math.Min(255, border.G + 28), Math.Min(255, border.B + 32));
            }

            using (GraphicsPath path = MainForm.CreateRoundedRect(rect, CornerRadius))
            {
                Color top = Color.FromArgb(Math.Min(255, bg.R + 8), Math.Min(255, bg.G + 10), Math.Min(255, bg.B + 14));
                using (LinearGradientBrush fill = new LinearGradientBrush(rect, top, bg, 90f))
                {
                    g.FillPath(fill, path);
                }
                Rectangle sheen = new Rectangle(rect.X + 1, rect.Y + 1, rect.Width - 2, Math.Max(4, rect.Height / 2));
                using (LinearGradientBrush sb = new LinearGradientBrush(
                    sheen, Color.FromArgb(20, 255, 255, 255), Color.FromArgb(0, 255, 255, 255), 90f))
                {
                    g.SetClip(path);
                    g.FillRectangle(sb, sheen);
                    g.ResetClip();
                }
                using (Pen pen = new Pen(border, 1.0f))
                {
                    g.DrawPath(pen, path);
                }
            }

            Rectangle tr = new Rectangle(6, 0, Math.Max(1, Width - 12), Height);
            TextRenderer.DrawText(
                g,
                Text ?? "",
                Font,
                tr,
                fg,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis | TextFormatFlags.NoPrefix);
        }
    }

    internal sealed class GlassSwitch : Control
    {
        private bool _checked;
        private bool _hovered;

        public Color ActiveTrackColor = Color.FromArgb(124, 178, 255);
        public Color TextColor = Color.FromArgb(243, 246, 251);

        public event EventHandler CheckedChanged;

        public bool Checked
        {
            get { return _checked; }
            set
            {
                if (_checked != value)
                {
                    _checked = value;
                    Invalidate();
                    EventHandler h = CheckedChanged;
                    if (h != null) h(this, EventArgs.Empty);
                }
            }
        }

        public GlassSwitch()
        {
            DoubleBuffered = true;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.SupportsTransparentBackColor, true);
            BackColor = Color.Transparent;
            Cursor = Cursors.Hand;
        }

        protected override void OnMouseEnter(EventArgs e) { _hovered = true; Invalidate(); base.OnMouseEnter(e); }
        protected override void OnMouseLeave(EventArgs e) { _hovered = false; Invalidate(); base.OnMouseLeave(e); }
        protected override void OnClick(EventArgs e)
        {
            Checked = !Checked;
            base.OnClick(e);
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;

            int trackW = 34;
            int trackH = 19;
            int ty = (Height - trackH) / 2;
            Rectangle trackRect = new Rectangle(0, ty, trackW, trackH);

            Color trackFill = _checked
                ? Color.FromArgb(165, ActiveTrackColor)
                : (_hovered ? Color.FromArgb(34, 43, 58) : Color.FromArgb(25, 32, 45));
            Color trackBorder = _checked
                ? Color.FromArgb(210, ActiveTrackColor)
                : Color.FromArgb(62, 74, 96);

            using (GraphicsPath tp = MainForm.CreateRoundedRect(trackRect, trackH / 2))
            {
                using (SolidBrush tb = new SolidBrush(trackFill)) g.FillPath(tb, tp);
                using (Pen pen = new Pen(trackBorder, 1.0f)) g.DrawPath(pen, tp);
            }

            int knobSize = 13;
            int kx = _checked ? (trackW - knobSize - 3) : 3;
            int ky = ty + (trackH - knobSize) / 2;
            Color knobColor = _checked ? Color.FromArgb(245, 248, 253) : Color.FromArgb(156, 168, 188);
            using (SolidBrush kb = new SolidBrush(knobColor))
            {
                g.FillEllipse(kb, kx, ky, knobSize, knobSize);
            }

            Rectangle textRect = new Rectangle(trackW + 10, 0, Math.Max(1, Width - trackW - 10), Height);
            TextRenderer.DrawText(
                g,
                Text ?? "",
                Font,
                textRect,
                TextColor,
                TextFormatFlags.Left | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis | TextFormatFlags.NoPrefix);
        }
    }

    internal sealed class CodeDigitTile : Control
    {
        public Color WellColor = Color.FromArgb(11, 15, 23);
        public Color RimColor = Color.FromArgb(52, 66, 90);
        public Color DigitColor = Color.FromArgb(243, 246, 251);

        public CodeDigitTile()
        {
            DoubleBuffered = true;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.SupportsTransparentBackColor, true);
            BackColor = Color.Transparent;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            Rectangle rect = new Rectangle(0, 0, Width - 1, Height - 1);
            using (GraphicsPath path = MainForm.CreateRoundedRect(rect, 13))
            {
                using (LinearGradientBrush bg = new LinearGradientBrush(
                    rect, Color.FromArgb(16, 21, 31), WellColor, 90f))
                {
                    g.FillPath(bg, path);
                }
                using (Pen pen = new Pen(RimColor, 1.0f))
                {
                    g.DrawPath(pen, path);
                }
            }
            TextRenderer.DrawText(
                g,
                Text ?? "0",
                Font,
                new Rectangle(0, 0, Width, Height),
                DigitColor,
                TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.NoPrefix);
        }
    }

    internal sealed class MainForm : Form
    {
        private const string ViGEmInstallerUrl = "https://github.com/nefarius/ViGEmBus/releases/download/v1.22.0/ViGEmBus_1.22.0_x64_x86_arm64.exe";

        private readonly HostEngine _engine;
        // Exact Android LinkPad Liquid Glass palette (Color.kt)
        private readonly Color _bg = Color.FromArgb(9, 12, 17);           // ObsidianBg
        private readonly Color _well = Color.FromArgb(12, 16, 23);        // OledWell
        private readonly Color _keycap = Color.FromArgb(27, 34, 48);      // KeycapSurface
        private readonly Color _textMain = Color.FromArgb(243, 246, 251); // TitaniumText
        private readonly Color _textMuted = Color.FromArgb(140, 151, 171);// PewterMuted
        private readonly Color _silver = Color.FromArgb(214, 223, 237);   // BrushedSilver
        private readonly Color _blue = Color.FromArgb(124, 178, 255);     // CobaltBlue
        private readonly Color _emerald = Color.FromArgb(92, 224, 168);   // SignalEmerald
        private readonly Color _amber = Color.FromArgb(229, 184, 105);    // WarmAmber
        private readonly Color _crimson = Color.FromArgb(242, 116, 116);  // PrecisionCrimson
        private readonly Color[] _slotColors = new Color[]
        {
            Color.FromArgb(92, 224, 168),   // P1 SignalEmerald
            Color.FromArgb(124, 178, 255),  // P2 CobaltBlue
            Color.FromArgb(229, 184, 105),  // P3 WarmAmber
            Color.FromArgb(192, 155, 255)   // P4 Violet
        };

        private GlassPill _ipPill;
        private GlassPill _ppsPill;
        private GlassButton _copyBtn;
        private readonly CodeDigitTile[] _digitBoxes = new CodeDigitTile[6];
        private int _ipCopiedUntilTick = 0;
        private int _codeCopiedUntilTick = 0;
        private bool _ipCopiedActive = false;
        private bool _codeCopiedActive = false;

        private GlassPill _driverBadgePill;
        private Label _driverStatusLabel;
        private Panel _progressTrack;
        private int _installPercent = -1;
        private int _marqueeTick = 0;
        private System.Windows.Forms.Timer _animTimer;

        private GlassButton _installDriverBtn;
        private GlassButton _modeToggleBtn;
        private GlassButton _joyCplBtn;
        private GlassButton _firewallBtn;
        private GlassSwitch _rememberSwitch;
        private GlassSwitch _openFavSwitch;
        private GlassSwitch _uniqueNamesSwitch;
        private Label _trustedCountLabel;
        private GlassButton _clearTrustBtn;

        private readonly GlassCardPanel[] _slotCards = new GlassCardPanel[4];
        private readonly GlassPill[] _slotBadges = new GlassPill[4];
        private readonly Label[] _slotTitles = new Label[4];
        private readonly Label[] _slotMasks = new Label[4];
        private readonly GlassButton[] _kickBtns = new GlassButton[4];

        private bool _keyboardOverride = false;
        private volatile bool _installingDriver = false;
        private string _installProgressText = "";

        public MainForm()
        {
            DoubleBuffered = true;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer, true);
            AutoScaleMode = AutoScaleMode.None;

            _engine = new HostEngine();
            _engine.StateChanged += delegate
            {
                if (!IsDisposed && IsHandleCreated)
                {
                    try { BeginInvoke((Action)RefreshUi); } catch { }
                }
            };

            Text = "LinkPad";
            ClientSize = new Size(580, 672);
            MinimumSize = new Size(580, 672);
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = _bg;
            ForeColor = _textMain;
            Font = new Font("Segoe UI", 9.5f);
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            Icon = CreateGamepadIcon();

            _animTimer = new System.Windows.Forms.Timer { Interval = 45 };
            _animTimer.Tick += delegate
            {
                if (_installingDriver && _installPercent < 0 && _progressTrack != null)
                {
                    _marqueeTick = (_marqueeTick + 5) % 140;
                    _progressTrack.Invalidate();
                }

                bool needRefresh = false;
                int now = Environment.TickCount;
                if (_ipCopiedActive && unchecked(now - _ipCopiedUntilTick) >= 0)
                {
                    _ipCopiedActive = false;
                    needRefresh = true;
                }
                if (_codeCopiedActive && unchecked(now - _codeCopiedUntilTick) >= 0)
                {
                    _codeCopiedActive = false;
                    if (_copyBtn != null) _copyBtn.Text = "Copy";
                    needRefresh = true;
                }
                if (needRefresh) RefreshUi();
                if (!_installingDriver && !_ipCopiedActive && !_codeCopiedActive)
                {
                    _animTimer.Stop();
                }
            };

            BuildLayout();
            _engine.Start();
            RefreshUi();
        }

        public static GraphicsPath CreateRoundedRect(Rectangle bounds, int radius)
        {
            return CreateRoundedRectF(new RectangleF(bounds.X, bounds.Y, bounds.Width, bounds.Height), radius);
        }

        public static GraphicsPath CreateRoundedRectF(RectangleF bounds, float radius)
        {
            float d = radius * 2f;
            GraphicsPath path = new GraphicsPath();
            if (d > bounds.Width) d = bounds.Width;
            if (d > bounds.Height) d = bounds.Height;
            if (d <= 0.1f)
            {
                path.AddRectangle(bounds);
                return path;
            }
            RectangleF arc = new RectangleF(bounds.Location, new SizeF(d, d));
            path.AddArc(arc, 180, 90);
            arc.X = bounds.Right - d;
            path.AddArc(arc, 270, 90);
            arc.Y = bounds.Bottom - d;
            path.AddArc(arc, 0, 90);
            arc.X = bounds.Left;
            path.AddArc(arc, 90, 90);
            path.CloseFigure();
            return path;
        }

        protected override void OnPaintBackground(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.Clear(_bg);
            g.SmoothingMode = SmoothingMode.AntiAlias;

            DrawAmbientGlow(g, new Rectangle(-120, -110, 440, 440), Color.FromArgb(26, 124, 178, 255));
            DrawAmbientGlow(g, new Rectangle(Width - 280, Height - 280, 420, 420), Color.FromArgb(20, 92, 224, 168));
        }

        private static void DrawAmbientGlow(Graphics g, Rectangle rect, Color centerColor)
        {
            using (GraphicsPath gp = new GraphicsPath())
            {
                gp.AddEllipse(rect);
                using (PathGradientBrush pgb = new PathGradientBrush(gp))
                {
                    pgb.CenterColor = centerColor;
                    pgb.SurroundColors = new Color[] { Color.Transparent };
                    g.FillPath(pgb, gp);
                }
            }
        }

        private void BuildLayout()
        {
            // Header
            GlassLogoBadge logoBadge = new GlassLogoBadge
            {
                Location = new Point(20, 15),
                Size = new Size(40, 40),
                BodyColor = _textMain,
                CutoutColor = Color.FromArgb(14, 19, 28)
            };
            Controls.Add(logoBadge);

            Label title = new Label
            {
                Text = "LinkPad",
                Font = new Font("Segoe UI Semibold", 15f),
                ForeColor = _textMain,
                BackColor = Color.Transparent,
                Location = new Point(70, 17),
                AutoSize = true
            };
            Controls.Add(title);

            GlassPill hostPill = new GlassPill
            {
                Text = "PC HOST",
                Location = new Point(158, 23),
                Size = new Size(68, 24),
                CornerRadius = 11,
                Font = new Font("Consolas", 8.2f, FontStyle.Bold),
                FillColor = Color.FromArgb(18, 27, 42),
                BorderColor = Color.FromArgb(64, 124, 178, 255),
                TextColor = _blue
            };
            Controls.Add(hostPill);

            _ppsPill = new GlassPill
            {
                Text = "0 pkt/s",
                Location = new Point(304, 23),
                Size = new Size(90, 24),
                CornerRadius = 11,
                Font = new Font("Consolas", 8.2f, FontStyle.Bold),
                FillColor = _well,
                BorderColor = Color.FromArgb(54, 92, 224, 168),
                DotColor = _emerald,
                TextColor = _emerald
            };
            Controls.Add(_ppsPill);

            _ipPill = new GlassPill
            {
                Text = HostEngine.GetPrimaryLocalIpv4() + ":" + Protocol.HOST_UDP_PORT,
                Location = new Point(402, 23),
                Size = new Size(158, 24),
                CornerRadius = 11,
                Font = new Font("Consolas", 8.2f, FontStyle.Bold),
                FillColor = _well,
                BorderColor = Color.FromArgb(52, 66, 90),
                TextColor = _silver,
                Cursor = Cursors.Hand
            };
            _ipPill.Click += delegate
            {
                try
                {
                    Clipboard.SetText(HostEngine.GetPrimaryLocalIpv4());
                    _ipCopiedActive = true;
                    _ipCopiedUntilTick = unchecked(Environment.TickCount + 1800);
                    _ipPill.Text = "IP Copied";
                    if (!_animTimer.Enabled) _animTimer.Start();
                }
                catch { }
            };
            Controls.Add(_ipPill);

            // 1. Pairing Code Card
            GlassCardPanel codeCard = new GlassCardPanel
            {
                Location = new Point(20, 68),
                Size = new Size(540, 114),
                CornerRadius = 20
            };
            Controls.Add(codeCard);

            Label codeCap = new Label
            {
                Text = "PAIRING CODE",
                Font = new Font("Segoe UI", 8.2f, FontStyle.Bold),
                ForeColor = _textMuted,
                Location = new Point(16, 13),
                AutoSize = true
            };
            codeCard.Controls.Add(codeCap);

            _copyBtn = CreateGlassButton("Copy", 358, 10, 74, 26, _keycap, _silver, 9);
            _copyBtn.Click += delegate
            {
                try
                {
                    Clipboard.SetText(_engine.PairingCode);
                    _copyBtn.Text = "Copied";
                    _codeCopiedActive = true;
                    _codeCopiedUntilTick = unchecked(Environment.TickCount + 1800);
                    if (!_animTimer.Enabled) _animTimer.Start();
                }
                catch { }
            };
            codeCard.Controls.Add(_copyBtn);

            GlassButton newCodeBtn = CreateGlassButton("New Code", 438, 10, 86, 26, _keycap, _textMain, 9);
            newCodeBtn.Click += delegate
            {
                _codeCopiedActive = false;
                _copyBtn.Text = "Copy";
                _engine.RegenerateCode();
            };
            codeCard.Controls.Add(newCodeBtn);

            for (int i = 0; i < 6; i++)
            {
                int xOffset = 16 + i * 84 + (i >= 3 ? 12 : 0);
                _digitBoxes[i] = new CodeDigitTile
                {
                    Text = "0",
                    Font = new Font("Consolas", 21f, FontStyle.Bold),
                    DigitColor = _textMain,
                    WellColor = _well,
                    RimColor = Color.FromArgb(50, 64, 88),
                    Location = new Point(xOffset, 44),
                    Size = new Size(76, 54)
                };
                codeCard.Controls.Add(_digitBoxes[i]);
            }

            // 2. Xbox 360 Virtual Controller Card
            GlassCardPanel driverCard = new GlassCardPanel
            {
                Location = new Point(20, 194),
                Size = new Size(540, 122),
                CornerRadius = 20
            };
            Controls.Add(driverCard);

            Label drvCap = new Label
            {
                Text = "XBOX 360 CONTROLLER (XINPUT)",
                Font = new Font("Segoe UI", 8.2f, FontStyle.Bold),
                ForeColor = _textMuted,
                Location = new Point(16, 13),
                AutoSize = true
            };
            driverCard.Controls.Add(drvCap);

            _driverBadgePill = new GlassPill
            {
                Text = "CHECKING",
                Location = new Point(428, 10),
                Size = new Size(96, 22),
                CornerRadius = 10,
                Font = new Font("Consolas", 7.8f, FontStyle.Bold),
                FillColor = Color.FromArgb(20, 32, 29),
                BorderColor = Color.FromArgb(72, 92, 224, 168),
                DotColor = _emerald,
                TextColor = _emerald
            };
            driverCard.Controls.Add(_driverBadgePill);

            _driverStatusLabel = new Label
            {
                Text = "Checking ViGEmBus...",
                Font = new Font("Segoe UI Semibold", 9.2f),
                ForeColor = _textMain,
                Location = new Point(16, 42),
                Size = new Size(508, 19)
            };
            driverCard.Controls.Add(_driverStatusLabel);

            _progressTrack = new Panel
            {
                Location = new Point(16, 56),
                Size = new Size(508, 9),
                BackColor = _well,
                Visible = false
            };
            _progressTrack.Paint += delegate (object s, PaintEventArgs ev)
            {
                Graphics g = ev.Graphics;
                g.SmoothingMode = SmoothingMode.AntiAlias;
                Rectangle tr = new Rectangle(0, 0, _progressTrack.Width - 1, _progressTrack.Height - 1);
                using (GraphicsPath tp = CreateRoundedRect(tr, 4))
                {
                    using (SolidBrush bgb = new SolidBrush(_well)) g.FillPath(bgb, tp);
                    if (_installingDriver)
                    {
                        g.SetClip(tp);
                        if (_installPercent >= 0)
                        {
                            int fillW = Math.Max(10, (int)(_progressTrack.Width * Math.Min(100, _installPercent) / 100.0));
                            Rectangle fr = new Rectangle(0, 0, fillW, _progressTrack.Height);
                            using (LinearGradientBrush fb = new LinearGradientBrush(fr, _blue, _emerald, 0f))
                            {
                                g.FillRectangle(fb, fr);
                            }
                        }
                        else
                        {
                            int segW = 140;
                            int xPos = (int)((_progressTrack.Width + segW) * (_marqueeTick / 140.0)) - segW;
                            Rectangle mr = new Rectangle(xPos, 0, segW, _progressTrack.Height);
                            using (LinearGradientBrush mb = new LinearGradientBrush(mr, _blue, _emerald, 0f))
                            {
                                g.FillRectangle(mb, mr);
                            }
                        }
                        g.ResetClip();
                    }
                    using (Pen pen = new Pen(Color.FromArgb(44, 255, 255, 255), 1))
                    {
                        g.DrawPath(pen, tp);
                    }
                }
            };
            driverCard.Controls.Add(_progressTrack);

            _installDriverBtn = CreateGlassButton("Install Xbox 360 Driver", 16, 76, 190, 32, _amber, Color.FromArgb(14, 18, 26), 10);
            _installDriverBtn.DisabledBgColor = Color.FromArgb(20, 44, 35);
            _installDriverBtn.DisabledTextColor = _emerald;
            _installDriverBtn.DisabledBorderColor = Color.FromArgb(56, 150, 112);
            _installDriverBtn.Click += delegate { InstallViGEmDriverAsync(); };
            driverCard.Controls.Add(_installDriverBtn);

            _modeToggleBtn = CreateGlassButton("Use Keyboard", 214, 76, 122, 32, _keycap, _textMain, 10);
            _modeToggleBtn.Click += delegate
            {
                _keyboardOverride = !_keyboardOverride;
                _engine.InitBestOutput(_keyboardOverride);
            };
            driverCard.Controls.Add(_modeToggleBtn);

            _joyCplBtn = CreateGlassButton("joy.cpl", 344, 76, 86, 32, _keycap, _blue, 10);
            _joyCplBtn.Click += delegate { OpenJoyCpl(); };
            driverCard.Controls.Add(_joyCplBtn);

            _firewallBtn = CreateGlassButton("Firewall", 438, 76, 86, 32, _keycap, _textMain, 10);
            _firewallBtn.Click += delegate { AllowFirewallRule(); };
            driverCard.Controls.Add(_firewallBtn);

            // 3. Favorites & Identity Card
            GlassCardPanel trustCard = new GlassCardPanel
            {
                Location = new Point(20, 328),
                Size = new Size(540, 122),
                CornerRadius = 20
            };
            Controls.Add(trustCard);

            _rememberSwitch = new GlassSwitch
            {
                Text = "Remember paired phones permanently (Favorites \u00B7 No Code)",
                Checked = _engine.RememberTrustedDevices,
                ActiveTrackColor = _blue,
                TextColor = _textMain,
                Font = new Font("Segoe UI Semibold", 9.2f),
                Location = new Point(16, 12),
                Size = new Size(498, 22)
            };
            _rememberSwitch.CheckedChanged += delegate { _engine.RememberTrustedDevices = _rememberSwitch.Checked; };
            trustCard.Controls.Add(_rememberSwitch);

            _openFavSwitch = new GlassSwitch
            {
                Text = "Allow new Favorites without code on this LAN",
                Checked = _engine.AllowAnyFavoriteWithoutCode,
                ActiveTrackColor = _emerald,
                TextColor = _textMuted,
                Font = new Font("Segoe UI", 9.0f),
                Location = new Point(16, 38),
                Size = new Size(410, 22)
            };
            _openFavSwitch.CheckedChanged += delegate { _engine.AllowAnyFavoriteWithoutCode = _openFavSwitch.Checked; };
            trustCard.Controls.Add(_openFavSwitch);

            _uniqueNamesSwitch = new GlassSwitch
            {
                Text = "Unique joy.cpl names (LinkPad P1\u2013P4 · avoids Cosmic Byte)",
                Checked = _engine.UniqueSlotNames,
                ActiveTrackColor = _amber,
                TextColor = _textMain,
                Font = new Font("Segoe UI", 9.0f),
                Location = new Point(16, 64),
                Size = new Size(430, 22)
            };
            _uniqueNamesSwitch.CheckedChanged += delegate
            {
                _engine.UniqueSlotNames = _uniqueNamesSwitch.Checked;
                if (!_keyboardOverride) _engine.InitBestOutput(false);
            };
            trustCard.Controls.Add(_uniqueNamesSwitch);

            _trustedCountLabel = new Label
            {
                Text = "0 paired phones remembered",
                Font = new Font("Consolas", 8.2f),
                ForeColor = _textMuted,
                Location = new Point(16, 94),
                AutoSize = true
            };
            trustCard.Controls.Add(_trustedCountLabel);

            _clearTrustBtn = CreateGlassButton("Clear Saved", 434, 89, 90, 25, Color.FromArgb(45, 24, 30), _crimson, 9);
            _clearTrustBtn.BorderColor = Color.FromArgb(110, 52, 60);
            _clearTrustBtn.DisabledBgColor = Color.FromArgb(24, 28, 36);
            _clearTrustBtn.DisabledTextColor = Color.FromArgb(95, 106, 124);
            _clearTrustBtn.DisabledBorderColor = Color.FromArgb(44, 52, 66);
            _clearTrustBtn.Click += delegate { _engine.ClearTrustedDevices(); };
            trustCard.Controls.Add(_clearTrustBtn);

            // 4. Players P1-P4 Bento Grid (2x2)
            Label playersHeader = new Label
            {
                Text = "PLAYERS \u00B7 P1 - P4",
                Font = new Font("Segoe UI", 8.2f, FontStyle.Bold),
                ForeColor = _textMuted,
                BackColor = Color.Transparent,
                Location = new Point(22, 462),
                AutoSize = true
            };
            Controls.Add(playersHeader);

            for (int i = 0; i < 4; i++)
            {
                int slot = i + 1;
                int col = i % 2;
                int row = i / 2;
                int cardX = 20 + col * 276;
                int cardY = 484 + row * 86;

                GlassCardPanel pCard = new GlassCardPanel
                {
                    Location = new Point(cardX, cardY),
                    Size = new Size(264, 76),
                    CornerRadius = 16
                };
                _slotCards[i] = pCard;
                Controls.Add(pCard);

                _slotBadges[i] = new GlassPill
                {
                    Text = "P" + slot,
                    Location = new Point(12, 12),
                    Size = new Size(36, 22),
                    CornerRadius = 9,
                    Font = new Font("Consolas", 8.5f, FontStyle.Bold),
                    FillColor = _well,
                    BorderColor = Color.FromArgb(56, 70, 94),
                    TextColor = _slotColors[i]
                };
                pCard.Controls.Add(_slotBadges[i]);

                _kickBtns[i] = CreateGlassButton("Kick", 198, 11, 54, 23, Color.FromArgb(48, 24, 30), _crimson, 8);
                _kickBtns[i].BorderColor = Color.FromArgb(115, 52, 60);
                _kickBtns[i].Visible = false;
                _kickBtns[i].Click += delegate { _engine.KickPlayer(slot); };
                pCard.Controls.Add(_kickBtns[i]);

                _slotTitles[i] = new Label
                {
                    Text = "Waiting",
                    Font = new Font("Segoe UI Semibold", 9.0f),
                    ForeColor = _textMuted,
                    Location = new Point(56, 13),
                    Size = new Size(138, 20),
                    AutoEllipsis = true
                };
                pCard.Controls.Add(_slotTitles[i]);

                _slotMasks[i] = new Label
                {
                    Text = "Waiting for phone",
                    Font = new Font("Consolas", 8.0f),
                    ForeColor = _textMuted,
                    Location = new Point(14, 44),
                    Size = new Size(236, 18),
                    AutoEllipsis = true
                };
                pCard.Controls.Add(_slotMasks[i]);
            }
        }

        private GlassButton CreateGlassButton(string text, int x, int y, int w, int h, Color bg, Color fg, int radius)
        {
            return new GlassButton
            {
                Text = text,
                Location = new Point(x, y),
                Size = new Size(w, h),
                CornerRadius = radius,
                BgColor = bg,
                TextColor = fg,
                BorderColor = Color.FromArgb(62, 76, 100),
                Font = new Font("Segoe UI Semibold", 8.5f)
            };
        }

        private void RefreshUi()
        {
            string ip = HostEngine.GetPrimaryLocalIpv4();
            if (!_ipCopiedActive)
            {
                _ipPill.Text = ip + ":" + Protocol.HOST_UDP_PORT;
            }

            string code = _engine.PairingCode;
            for (int i = 0; i < 6 && i < code.Length; i++)
            {
                _digitBoxes[i].Text = code[i].ToString();
            }
            _ppsPill.Text = _engine.PacketsPerSecond + " pkt/s";

            int tc = _engine.TrustedCount;
            _trustedCountLabel.Text = tc == 0 ? "No saved phones yet" : (tc + " paired phone" + (tc == 1 ? "" : "s") + " remembered");
            _clearTrustBtn.Enabled = tc > 0;

            if (_installingDriver)
            {
                if (!_animTimer.Enabled) _animTimer.Start();
                string stepText = string.IsNullOrEmpty(_installProgressText) ? "Installing..." : _installProgressText;
                _driverBadgePill.Text = "INSTALLING";
                _driverBadgePill.DotColor = _amber;
                _driverBadgePill.TextColor = _amber;
                _driverBadgePill.BorderColor = Color.FromArgb(80, 229, 184, 105);
                _driverStatusLabel.Text = stepText;
                _driverStatusLabel.ForeColor = _amber;
                _driverStatusLabel.Location = new Point(16, 33);
                _progressTrack.Visible = true;
                _installDriverBtn.Text = _installPercent >= 0 ? ("Installing " + _installPercent + "%") : "Installing...";
                _installDriverBtn.Enabled = false;
                _installDriverBtn.DisabledBgColor = Color.FromArgb(44, 38, 22);
                _installDriverBtn.DisabledTextColor = _amber;
                _installDriverBtn.DisabledBorderColor = Color.FromArgb(120, 96, 48);
            }
            else if (_engine.IsXboxActive)
            {
                if (_animTimer.Enabled && !_ipCopiedActive && !_codeCopiedActive) _animTimer.Stop();
                _driverBadgePill.Text = "XINPUT OK";
                _driverBadgePill.DotColor = _emerald;
                _driverBadgePill.TextColor = _emerald;
                _driverBadgePill.BorderColor = Color.FromArgb(72, 92, 224, 168);
                _driverStatusLabel.Text = _engine.UniqueSlotNames
                    ? "LinkPad P1\u2013P4 Virtual Controllers Active \u00B7 Open joy.cpl"
                    : "LinkPad Controller (XInput) Active \u00B7 Open joy.cpl";
                _driverStatusLabel.ForeColor = _emerald;
                _driverStatusLabel.Location = new Point(16, 42);
                _progressTrack.Visible = false;
                _installDriverBtn.Text = "Xbox Driver Ready";
                _installDriverBtn.Enabled = false;
                _installDriverBtn.DisabledBgColor = Color.FromArgb(19, 44, 35);
                _installDriverBtn.DisabledTextColor = _emerald;
                _installDriverBtn.DisabledBorderColor = Color.FromArgb(56, 150, 112);
            }
            else if (_keyboardOverride)
            {
                if (_animTimer.Enabled && !_ipCopiedActive && !_codeCopiedActive) _animTimer.Stop();
                _driverBadgePill.Text = "KEYBOARD";
                _driverBadgePill.DotColor = _blue;
                _driverBadgePill.TextColor = _blue;
                _driverBadgePill.BorderColor = Color.FromArgb(72, 124, 178, 255);
                _driverStatusLabel.Text = "Keyboard Mode (WASD / Arrows / IJKL / Numpad)";
                _driverStatusLabel.ForeColor = _blue;
                _driverStatusLabel.Location = new Point(16, 42);
                _progressTrack.Visible = false;
                _installDriverBtn.Text = "Retry Xbox Driver";
                _installDriverBtn.Enabled = true;
                _installDriverBtn.BgColor = _keycap;
                _installDriverBtn.TextColor = _textMain;
            }
            else
            {
                if (_animTimer.Enabled && !_ipCopiedActive && !_codeCopiedActive) _animTimer.Stop();
                _driverBadgePill.Text = "REQUIRED";
                _driverBadgePill.DotColor = _amber;
                _driverBadgePill.TextColor = _amber;
                _driverBadgePill.BorderColor = Color.FromArgb(80, 229, 184, 105);
                _driverStatusLabel.Text = string.IsNullOrEmpty(_engine.LastStatusError)
                    ? "Click Install Xbox 360 Driver to register controllers in Windows"
                    : _engine.LastStatusError;
                _driverStatusLabel.ForeColor = _amber;
                _driverStatusLabel.Location = new Point(16, 42);
                _progressTrack.Visible = false;
                _installDriverBtn.Text = "Install Xbox 360 Driver";
                _installDriverBtn.Enabled = true;
                _installDriverBtn.BgColor = _amber;
                _installDriverBtn.TextColor = Color.FromArgb(14, 18, 26);
            }

            _driverBadgePill.Invalidate();
            _progressTrack.Invalidate();
            _modeToggleBtn.Text = _keyboardOverride ? "Use Xbox 360" : "Use Keyboard";

            PlayerSlotState[] slots = _engine.GetSlotsSnapshot();
            for (int i = 0; i < 4; i++)
            {
                PlayerSlotState s = slots[i];
                if (s.Connected)
                {
                    string rtt = s.RttMs > 0 ? (s.RttMs + "ms") : "LAN";
                    string trustTag = s.IsTrusted ? " \u00B7 Saved" : "";
                    _slotTitles[i].Text = string.Format("{0} ({1}{2})", s.DeviceName, rtt, trustTag);
                    _slotTitles[i].ForeColor = _textMain;
                    _slotTitles[i].Size = new Size(138, 20);
                    _slotMasks[i].Text = Protocol.MaskToText(s.ButtonMask, s.Layout, s.Lx, s.Ly, s.Rx, s.Ry);
                    _slotMasks[i].ForeColor = _slotColors[i];
                    _slotBadges[i].BorderColor = _slotColors[i];
                    _slotCards[i].AccentBorder = _slotColors[i];
                    _slotCards[i].Invalidate();
                    _slotBadges[i].Invalidate();
                    _kickBtns[i].Visible = true;
                }
                else
                {
                    bool p1Ready = (i == 0 && _engine.IsXboxActive);
                    _slotTitles[i].Text = p1Ready ? (_engine.UniqueSlotNames ? "LinkPad P1" : "LinkPad Controller") : "Waiting";
                    _slotTitles[i].ForeColor = p1Ready ? _silver : _textMuted;
                    _slotTitles[i].Size = new Size(192, 20);
                    _slotMasks[i].Text = p1Ready ? "Plugged in Windows (joy.cpl)" : "Connect phone via code or LAN";
                    _slotMasks[i].ForeColor = p1Ready ? _emerald : _textMuted;
                    _slotBadges[i].BorderColor = p1Ready ? _emerald : Color.FromArgb(56, 70, 94);
                    _slotCards[i].AccentBorder = Color.Empty;
                    _slotCards[i].Invalidate();
                    _slotBadges[i].Invalidate();
                    _kickBtns[i].Visible = false;
                }
            }
        }

        private void OpenJoyCpl()
        {
            try
            {
                if (!_engine.IsXboxActive && !_installingDriver)
                {
                    DialogResult r = MessageBox.Show(
                        this,
                        "The Xbox 360 virtual controller driver is not active yet, so joy.cpl may be empty.\n\nInstall / Activate Xbox 360 Driver now?",
                        "LinkPad",
                        MessageBoxButtons.YesNo,
                        MessageBoxIcon.Information);
                    if (r == DialogResult.Yes)
                    {
                        InstallViGEmDriverAsync();
                        return;
                    }
                }
                Process.Start("joy.cpl");
            }
            catch { }
        }

        private void SetInstallProgress(string text, int percent)
        {
            _installProgressText = text;
            _installPercent = percent;
            if (!IsDisposed && IsHandleCreated)
            {
                try { BeginInvoke((Action)RefreshUi); } catch { }
            }
        }

        private void InstallViGEmDriverAsync()
        {
            if (_installingDriver) return;
            _installingDriver = true;
            SetInstallProgress("Step 1/3 \u00B7 Checking ViGEm Client...", 10);

            new Thread(delegate ()
            {
                int exitCode = 0;
                bool installerRan = false;
                string failureMsg = "";
                try
                {
                    string dllErr;
                    if (!ViGEmXboxOutput.EnsureClientDll(out dllErr))
                    {
                        failureMsg = "Could not download ViGEm Client DLL:\n" + dllErr;
                        return;
                    }

                    SetInstallProgress("Checking ViGEmBus Service...", 25);
                    _keyboardOverride = false;
                    if (_engine.InitBestOutput(false))
                    {
                        SetInstallProgress("Xbox 360 Driver Active!", 100);
                        return;
                    }

                    string appDataDir = Path.Combine(
                        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                        "LinkPad");
                    if (!Directory.Exists(appDataDir)) Directory.CreateDirectory(appDataDir);
                    string installerPath = Path.Combine(appDataDir, "ViGEmBus_1.22.0_x64_x86_arm64.exe");

                    bool cachedValid = false;
                    try
                    {
                        if (File.Exists(installerPath) && new FileInfo(installerPath).Length >= 500000)
                        {
                            byte[] hdr = new byte[2];
                            using (FileStream fs = File.OpenRead(installerPath))
                            {
                                if (fs.Read(hdr, 0, 2) == 2 && hdr[0] == 0x4D && hdr[1] == 0x5A) cachedValid = true;
                            }
                        }
                    }
                    catch { }

                    if (!cachedValid)
                    {
                        try { if (File.Exists(installerPath)) File.Delete(installerPath); } catch { }
                        SetInstallProgress("Step 2/3 \u00B7 Downloading Driver 0%...", 0);
                        string dlErr;
                        bool ok = ViGEmXboxOutput.DownloadFileManaged(
                            ViGEmInstallerUrl,
                            installerPath,
                            500000,
                            new byte[] { 0x4D, 0x5A },
                            delegate (int pct)
                            {
                                SetInstallProgress("Step 2/3 \u00B7 Downloading Driver " + pct + "%", pct);
                            },
                            out dlErr);
                        if (!ok)
                        {
                            failureMsg = "Could not download ViGEmBus installer:\n" + dlErr;
                            return;
                        }
                    }

                    SetInstallProgress("Step 3/3 \u00B7 Complete ViGEmBus Setup (Approve UAC)...", -1);
                    ProcessStartInfo psi = new ProcessStartInfo
                    {
                        FileName = installerPath,
                        UseShellExecute = true,
                        Verb = "runas"
                    };
                    Process proc = Process.Start(psi);
                    if (proc != null)
                    {
                        installerRan = true;
                        proc.WaitForExit();
                        exitCode = proc.ExitCode;
                    }
                }
                catch (Win32Exception wex)
                {
                    if (wex.NativeErrorCode == 1223)
                    {
                        failureMsg = "Administrator permission (UAC) was cancelled. Please click Yes on the UAC prompt to install the Xbox 360 driver.";
                    }
                    else
                    {
                        failureMsg = wex.Message;
                    }
                }
                catch (Exception ex)
                {
                    failureMsg = ex.Message;
                }
                finally
                {
                    SetInstallProgress("Activating Xbox 360 Controller...", -1);
                    _keyboardOverride = false;
                    bool xboxReady = _engine.InitBestOutput(false);
                    for (int attempt = 0; attempt < 3 && !xboxReady && installerRan; attempt++)
                    {
                        Thread.Sleep(650);
                        xboxReady = _engine.InitBestOutput(false);
                    }

                    _installingDriver = false;
                    _installProgressText = "";
                    _installPercent = xboxReady ? 100 : -1;

                    if (!IsDisposed && IsHandleCreated)
                    {
                        BeginInvoke((Action)delegate
                        {
                            RefreshUi();
                            if (!string.IsNullOrEmpty(failureMsg))
                            {
                                DialogResult res = MessageBox.Show(
                                    this,
                                    failureMsg + "\n\nOpen the official ViGEmBus download page in your browser instead?",
                                    "LinkPad Xbox 360 Driver Setup",
                                    MessageBoxButtons.YesNo,
                                    MessageBoxIcon.Warning);
                                if (res == DialogResult.Yes)
                                {
                                    try { Process.Start("https://github.com/nefarius/ViGEmBus/releases/tag/v1.22.0"); } catch { }
                                }
                            }
                            else if (xboxReady)
                            {
                                MessageBox.Show(
                                    this,
                                    "Xbox 360 Virtual Controller is plugged in and active! Click joy.cpl to see it in Windows.",
                                    "LinkPad Ready",
                                    MessageBoxButtons.OK,
                                    MessageBoxIcon.Information);
                            }
                            else if (installerRan && exitCode == 3010)
                            {
                                MessageBox.Show(
                                    this,
                                    "ViGEmBus installation finished! Please restart Windows once so the Xbox 360 driver loads.",
                                    "Restart Required",
                                    MessageBoxButtons.OK,
                                    MessageBoxIcon.Information);
                            }
                            else if (installerRan)
                            {
                                MessageBox.Show(
                                    this,
                                    "Setup finished (code " + exitCode + "), but ViGEmBus is not active yet:\n" +
                                    (_engine.LastStatusError ?? "Unknown error") +
                                    "\n\nTip: If you finished the wizard, restart Windows or check Windows Security > Core Isolation (Memory Integrity).",
                                    "LinkPad Driver Setup",
                                    MessageBoxButtons.OK,
                                    MessageBoxIcon.Warning);
                            }
                        });
                    }
                }
            }) { IsBackground = true }.Start();
        }

        private void AllowFirewallRule()
        {
            const string ruleName = "LinkPad UDP 47850-47851";
            try
            {
                Type policyType = Type.GetTypeFromProgID("HNetCfg.FwPolicy2");
                Type ruleType = Type.GetTypeFromProgID("HNetCfg.FWRule");
                if (policyType == null || ruleType == null)
                {
                    MessageBox.Show(this, "Windows Firewall COM service is unavailable. Allow LinkPad UDP 47850-47851 in Windows Security.",
                        "LinkPad", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                    return;
                }

                object policy = Activator.CreateInstance(policyType);
                object rules = policyType.InvokeMember("Rules", BindingFlags.GetProperty, null, policy, null);

                try
                {
                    rules.GetType().InvokeMember("Remove", BindingFlags.InvokeMethod, null, rules, new object[] { ruleName });
                }
                catch { }

                object rule = Activator.CreateInstance(ruleType);
                ruleType.InvokeMember("Name", BindingFlags.SetProperty, null, rule, new object[] { ruleName });
                ruleType.InvokeMember("Description", BindingFlags.SetProperty, null, rule, new object[] { "Allow LinkPad LAN Gamepad UDP discovery & input" });
                ruleType.InvokeMember("Protocol", BindingFlags.SetProperty, null, rule, new object[] { 17 }); // NET_FW_IP_PROTOCOL_UDP
                ruleType.InvokeMember("LocalPorts", BindingFlags.SetProperty, null, rule, new object[] { "47850-47851" });
                ruleType.InvokeMember("Direction", BindingFlags.SetProperty, null, rule, new object[] { 1 }); // NET_FW_RULE_DIR_IN
                ruleType.InvokeMember("Action", BindingFlags.SetProperty, null, rule, new object[] { 1 }); // NET_FW_ACTION_ALLOW
                ruleType.InvokeMember("Profiles", BindingFlags.SetProperty, null, rule, new object[] { int.MaxValue });
                ruleType.InvokeMember("Enabled", BindingFlags.SetProperty, null, rule, new object[] { true });

                rules.GetType().InvokeMember("Add", BindingFlags.InvokeMethod, null, rules, new object[] { rule });
                MessageBox.Show(this, "Windows Firewall updated for LinkPad (UDP 47850-47851).",
                    "LinkPad", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
            catch (TargetInvocationException tie)
            {
                Exception inner = tie.InnerException ?? tie;
                string msg = (inner is UnauthorizedAccessException || (inner is COMException && ((COMException)inner).ErrorCode == unchecked((int)0x80070005)))
                    ? "Administrator access is needed once to add the Windows Firewall rule.\n\nRight-click LinkPadHost.exe -> Run as administrator, then click Firewall."
                    : ("Could not update Windows Firewall: " + inner.Message);
                MessageBox.Show(this, msg, "LinkPad Firewall", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, "Could not update Windows Firewall: " + ex.Message,
                    "LinkPad Firewall", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
        }

        private static Icon CreateGamepadIcon()
        {
            Bitmap bmp = new Bitmap(64, 64);
            using (Graphics g = Graphics.FromImage(bmp))
            {
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.Clear(Color.Transparent);
                using (GraphicsPath bg = CreateRoundedRect(new Rectangle(2, 2, 60, 60), 14))
                {
                    using (SolidBrush sb = new SolidBrush(Color.FromArgb(18, 23, 32)))
                    {
                        g.FillPath(sb, bg);
                    }
                    using (Pen rim = new Pen(Color.FromArgb(72, 255, 255, 255), 1.5f))
                    {
                        g.DrawPath(rim, bg);
                    }
                }
                BrandGlyph.DrawControllerLogo(
                    g,
                    new RectangleF(8f, 8f, 48f, 48f),
                    Color.FromArgb(240, 244, 250),
                    Color.FromArgb(18, 23, 32));
            }
            return Icon.FromHandle(bmp.GetHicon());
        }

        protected override void OnFormClosed(FormClosedEventArgs e)
        {
            if (_animTimer != null) _animTimer.Stop();
            _engine.Dispose();
            base.OnFormClosed(e);
        }
    }

    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }
}
