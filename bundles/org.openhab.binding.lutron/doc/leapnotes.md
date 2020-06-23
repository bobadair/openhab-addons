# LEAP Protocol Notes

## Protocol support for different Lutron bridge devices

|Bridge Device           | LIP | LEAP |
|------------------------|-----|------|
|HomeWorks QS Processor  |  X  |      |
|RadioRA 2 Main Repeater |  X  |      |
|RA2 Select Main Repeater|  X  |  X   |
|Caseta Smart Bridge Pro |  X  |  X   |
|Caseta Smart Bridge     |     |  X   |

## Configuring LEAP Authentication

Unlike LIP, which was designed to use a simple serial or telnet connection and authenticates using a username/password, LEAP uses a SSL connection and authenticates using certificates.
This necessarily makes configuration more complicated.
At the moment, the necessary certificate files can be generated using the get_lutron_cert.py script available from https://github.com/gurumitts/pylutron-caseta .
On a unix system, you can easily retrieve it using curl with a command like:

```
curl https://raw.githubusercontent.com/gurumitts/pylutron-caseta/dev/get_lutron_cert.py >get_lutron_cert.py
```

Remember that the get_lutron_cert.py script must be run using python3, not 2!
Also, the script will prompt you to press the button on your smart hub to authorize key generation, so you should be somewhere near the hub when you run it.
Running it will not affect your existing hub configuration or Lutron app installations.
When it has completed, it will have generated three files: caseta.crt, caseta.key, and caseta-bridge.crt.

Once the key and certificate files have been generated, you will need to load them into a java keystore.
You’ll then need to set the ipAddress, keystore, and keystorePassword parameters of the leapbridge thing.
Ignore any other key-related bridge options, as it will only work with a java keystore right now.

You can load a keystore from the key and certificate files with the following commands.
You’ll need access to both the java keytool and openssl.

```
openssl pkcs12 -export -in caseta.crt -inkey caseta.key -out caseta.p12 -name caseta

keytool -importkeystore -destkeystore lutron.keystore -srckeystore caseta.p12 -srcstoretype PKCS12 -srcstorepass secret -alias caseta

keytool -importcert -file caseta-bridge.crt -keystore lutron.keystore -alias caseta-bridge
```

Respond to the password prompt(s) with a password, and then use that password in the -srcstorepass parameter of the keytool command and in the keystorePassword parameter for leapbridge.
In the example above, the pkcs12 store password was set to “secret”, but hopefully you can think of a better one.
The lutron.keystore file that you end up with is the one you’ll need to give the binding access to.
The caseta.p12 file is just an intermediate file that you can delete later.

## Functional differences between LIP and LEAP

* Using LIP on Caseta you can’t receive notifications of occupancy group status changes (occupied/unoccupied/unknown), but using LEAP you can.
* Conversely, LIP provides notifications of keypad key presses, while LEAP does not (as far as is currently known). This means that using ipbridge you can trigger rules and take actions on keypad key presses/releases, but using leapbridge you can’t.
* Caseta and RA2 Select device discovery is supported via LEAP.
* LIP is a publicly documented and supported protocol, while LEAP is not. This means that Lutron could make a change that breaks LEAP support at any time.

## Running both LIP and LEAP
It is possible to run leapbridge and ipbridge at the same time, for the same bridge device, but each device should only be configured through one bridge.
Remember that LEAP device ids and LIP device IDs are not necessarily equal!
