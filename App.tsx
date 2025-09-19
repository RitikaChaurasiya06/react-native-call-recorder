import React, { useEffect } from 'react';
import { PermissionsAndroid, Platform, View, Text } from 'react-native';

export default function App() {
  useEffect(() => {
    async function requestPermissions() {
      if (Platform.OS === 'android') {
        try {
          const granted = await PermissionsAndroid.requestMultiple([
            PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
            PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
            PermissionsAndroid.PERMISSIONS.WRITE_EXTERNAL_STORAGE,
            PermissionsAndroid.PERMISSIONS.READ_CALL_LOG,
          ]);
          console.log('Permissions result:', granted);
        } catch (err) {
          console.warn(err);
        }
      }
    }

    requestPermissions();
  }, []);

  return (
    <View>
      <Text>Call Recorder App</Text>
    </View>
  );
}
